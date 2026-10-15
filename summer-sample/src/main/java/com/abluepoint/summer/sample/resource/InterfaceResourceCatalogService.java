package com.abluepoint.summer.sample.resource;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
final class InterfaceResourceCatalogService implements SmartInitializingSingleton, org.springframework.context.ApplicationListener<org.springframework.context.event.ContextRefreshedEvent> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InterfaceResourceCatalogService.class);
    private final ApplicationContext context;
    private final InterfaceResourceHistory history;
    private final Map<String, ApplicationContext> contexts = new java.util.LinkedHashMap<>();
    private volatile InterfaceResourceCatalogView current = new InterfaceResourceCatalogView(List.of(), 0);
    private volatile InterfaceResourceCatalog catalog = new InterfaceResourceCatalog(List.of());

    InterfaceResourceCatalogService(ApplicationContext context, InterfaceResourceHistory history) {
        this.context = context;
        this.history = history;
        this.contexts.put("application", context);
    }

    @Override
    public void afterSingletonsInstantiated() { refresh(); }

    @Override
    public synchronized void onApplicationEvent(org.springframework.context.event.ContextRefreshedEvent event) {
        ApplicationContext candidate = event.getApplicationContext();
        for (ApplicationContext ancestor = candidate; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor == context) {
                contexts.put(candidate == context ? "application" : "management", candidate);
                refresh();
                return;
            }
        }
    }

    private synchronized void refresh() {
        List<InterfaceResourceDescriptor> descriptors = new ArrayList<>();
        List<InterfaceResourceDiscoveryIssue> issues = new ArrayList<>();
        contexts.forEach((id, active) -> {
            if (!(active instanceof org.springframework.context.ConfigurableApplicationContext configurable) || configurable.isActive())
                descriptors.addAll(discover(active, id, issues));
        });
        long previous = current.revision() == 0 ? history.latestRevision() : current.revision();
        catalog = history.protect(new InterfaceResourceCatalog(descriptors, issues));
        current = history.record(catalog);
        if (previous != current.revision()) {
            var difference = previous == 0 ? null : history.diff(previous, current.revision());
            log.info("Interface resource revision={} added={} removed={} changed={} complete={}", current.revision(),
                    difference == null ? current.items().size() : difference.added().size(),
                    difference == null ? 0 : difference.removed().size(), difference == null ? 0 : difference.changed().size(), current.complete());
        }
    }

    private List<InterfaceResourceDescriptor> discover(ApplicationContext context, String contextId, List<InterfaceResourceDiscoveryIssue> issues) {
        List<InterfaceResourceDescriptor> descriptors = new ArrayList<>();
        List<InterfaceResourceContributor> contributors = List.copyOf(context.getBeansOfType(InterfaceResourceContributor.class).values());
        List<HandlerMapping> mappings = List.copyOf(context.getBeansOfType(HandlerMapping.class).values());
        for (HandlerMapping candidate : mappings) {
            var contributed = contributors.stream().map(provider -> provider.mapping(candidate)).filter(java.util.Optional::isPresent).findFirst();
            if (contributed.isPresent()) { descriptors.addAll(contributed.get().get()); continue; }
            if (candidate instanceof AbstractHandlerMethodMapping<?> mapping) {
                for (var entry : mapping.getHandlerMethods().entrySet()) {
                    if (!(entry.getKey() instanceof RequestMappingInfo info)) { issues.add(incomplete(contextId, candidate)); continue; }
                    var paths = info.getPatternValues().isEmpty() ? Set.of("/") : info.getPatternValues();
                    var methods = info.getMethodsCondition().getMethods();
                    for (String path : paths) {
                        var effective = methods.isEmpty() ? java.util.EnumSet.allOf(RequestMethod.class) : java.util.EnumSet.copyOf(methods);
                        if (effective.contains(RequestMethod.GET)) effective.add(RequestMethod.HEAD);
                        for (RequestMethod method : effective) descriptors.add(descriptor(path, method, entry.getValue(), info));
                    }
                }
            } else if (candidate instanceof org.springframework.web.servlet.function.support.RouterFunctionMapping mapping) {
                if (mapping.getRouterFunction() != null) {
                    try { descriptors.addAll(FunctionalResourceDiscovery.discover(mapping.getRouterFunction(), () -> issues.add(incomplete(contextId, candidate)))); }
                    catch (IllegalStateException unsupported) { issues.add(incomplete(contextId, candidate)); }
                }
            } else if (candidate instanceof AbstractUrlHandlerMapping mapping) {
                Map<String, Object> urls = new java.util.LinkedHashMap<>(mapping.getHandlerMap());
                if (mapping.getRootHandler() != null) urls.putIfAbsent("/", mapping.getRootHandler());
                if (mapping.getDefaultHandler() != null) urls.putIfAbsent("/*", mapping.getDefaultHandler());
                urls.forEach((path, value) -> {
                    Object handler = value instanceof String beanName ? context.getBean(beanName) : value;
                    ResourceType type = handler instanceof org.springframework.web.servlet.resource.ResourceHttpRequestHandler
                            ? ResourceType.STATIC : handler instanceof org.springframework.web.socket.server.support.WebSocketHttpRequestHandler
                            ? ResourceType.PROTOCOL : handler instanceof org.springframework.web.servlet.mvc.ParameterizableViewController
                            ? classify(path) : ResourceType.BUSINESS;
                    descriptors.add(new InterfaceResourceDescriptor("ANY", path, handler.getClass().isSynthetic() ? handler.getClass().getNestHost().getName() + "#urlHandler" : handler.getClass().getName(),
                            module(handler.getClass()), type, SensitiveLevel.SENSITIVE, null,
                            type == ResourceType.PROTOCOL || type == ResourceType.INFRASTRUCTURE,
                            contextId, "URL_MAPPING", Map.of(), type == ResourceType.STATIC ? "STATIC_MAPPING" : type == ResourceType.BUSINESS ? "MANAGED_ADAPTER_REQUIRED" : "COMPONENT_OWNED"));
                });
            } else if (!coveredManagementComposite(candidate, mappings)) issues.add(incomplete(contextId, candidate));
        }
        for (SecurityFilterChain chain : context.getBeansOfType(SecurityFilterChain.class).values()) {
            for (var filter : chain.getFilters()) {
                var contributed = contributors.stream().map(provider -> provider.filter(filter)).filter(java.util.Optional::isPresent).findFirst();
                if (contributed.isPresent()) { descriptors.addAll(contributed.get().get()); continue; }
                String name = filter.getClass().getName();
                if (!(name.startsWith("org.springframework.security.oauth2.server.authorization.") && name.endsWith("EndpointFilter"))
                        && !(filter instanceof org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter)
                        && !(filter instanceof org.springframework.security.web.authentication.logout.LogoutFilter)) continue;
                org.springframework.util.ReflectionUtils.doWithFields(filter.getClass(), field -> {
                    if (!org.springframework.security.web.util.matcher.RequestMatcher.class.isAssignableFrom(field.getType())) return;
                    org.springframework.util.ReflectionUtils.makeAccessible(field);
                    List<InterfaceResourceDescriptor> found = new ArrayList<>();
                    discoverMatcher(org.springframework.util.ReflectionUtils.getField(field, filter), name, found);
                    boolean metadata = name.endsWith("OidcProviderConfigurationEndpointFilter") || name.endsWith("OAuth2AuthorizationServerMetadataEndpointFilter");
                    var settings = context.getBeanProvider(org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings.class).getIfAvailable();
                    if (found.isEmpty()) issues.add(incomplete(contextId, filter));
                    found.stream().filter(item -> !metadata || item.normalizedPath().contains("**") == (settings != null && settings.isMultipleIssuersAllowed()))
                            .filter(item -> !(chain instanceof org.springframework.security.web.DefaultSecurityFilterChain concrete)
                                    || !Boolean.FALSE.equals(chainAllows(concrete.getRequestMatcher(), item)))
                            .forEach(descriptors::add);
                });
            }
        }
        return descriptors.stream().map(item -> new InterfaceResourceDescriptor(item.httpMethod(), item.normalizedPath(),
                item.handler(), item.module(), item.resourceType(), item.sensitiveLevel(), item.permissionCode(), item.configurable(),
                contextId, item.source(), item.conditions(), item.configurationPolicy())).toList();
    }

    /** Boot's management wrapper has no routes of its own; each delegate must still be scanned. */
    private static boolean coveredManagementComposite(HandlerMapping candidate, List<HandlerMapping> mappings) {
        if (!candidate.getClass().getName().equals(
                "org.springframework.boot.webmvc.autoconfigure.actuate.web.CompositeHandlerMapping")) return false;
        try {
            Method method = candidate.getClass().getDeclaredMethod("getMappings");
            org.springframework.util.ReflectionUtils.makeAccessible(method);
            Object value = method.invoke(candidate);
            return value instanceof List<?> delegates && delegates.stream().allMatch(delegate ->
                    delegate != candidate && mappings.stream().anyMatch(mapping -> mapping == delegate));
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
    }

    InterfaceResourceCatalogView read(Authentication authentication) {
        requirePlatformRead(authentication);
        refresh();
        return current;
    }

    InterfaceResourceCatalog snapshot() {
        return catalog;
    }

    private static InterfaceResourceDescriptor descriptor(String path, RequestMethod method, HandlerMethod handler, RequestMappingInfo mapping) {
        String httpMethod = method == null ? "ANY" : method.name();
        Method methodRef = handler.getMethod();
        ResourcePermission explicit = AnnotatedElementUtils.findMergedAnnotation(methodRef, ResourcePermission.class);
        if (explicit == null) {
            explicit = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
        }
        ResourceType type = handler.getBeanType().getName().startsWith("org.springframework.boot.")
                ? ResourceType.INFRASTRUCTURE : classify(path);
        SensitiveLevel sensitivity = sensitivity(path, httpMethod, type);
        String module = module(handler.getBeanType());
        Map<String, List<String>> conditions = new java.util.TreeMap<>();
        conditions.put("declaredMethods", mapping.getMethodsCondition().getMethods().stream().map(Enum::name).toList());
        conditions.put("params", mapping.getParamsCondition().getExpressions().stream().map(Object::toString).map(InterfaceResourceCatalogService::safeExpression).toList());
        conditions.put("headers", mapping.getHeadersCondition().getExpressions().stream().map(Object::toString).map(InterfaceResourceCatalogService::safeExpression).toList());
        conditions.put("consumes", mapping.getConsumesCondition().getExpressions().stream().map(Object::toString).toList());
        conditions.put("produces", mapping.getProducesCondition().getExpressions().stream().map(Object::toString).toList());
        if (!mapping.getVersionCondition().isEmpty()) conditions.put("version", List.of(mapping.getVersionCondition().toString()));
        if (mapping.getCustomCondition() != null) conditions.put("custom", List.of(mapping.getCustomCondition().getClass().getName()));
        return new InterfaceResourceDescriptor(httpMethod, path,
                handlerIdentity(handler),
                module, type, sensitivity, explicit == null ? null : explicit.value(), true,
                "application", "MVC", conditions, type == ResourceType.BUSINESS ? "MANAGED_ROLE" : "COMPONENT_OWNED");
    }

    private static InterfaceResourceDiscoveryIssue incomplete(String contextId, Object source) {
        String name = source.getClass().isSynthetic() ? source.getClass().getNestHost().getName() + "#opaque" : source.getClass().getName();
        return new InterfaceResourceDiscoveryIssue(contextId, name, "SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED", "An explicit metadata contributor is required for this runtime component");
    }

    /** Only exclude an endpoint when a known chain matcher proves it unreachable. */
    private static Boolean chainAllows(Object matcher, InterfaceResourceDescriptor item) {
        if (matcher instanceof org.springframework.security.web.util.matcher.AnyRequestMatcher) return true;
        if (matcher instanceof org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher) {
            if (item.normalizedPath().contains("*") || item.normalizedPath().contains("{")) return null;
            var pattern = (org.springframework.web.util.pattern.PathPattern) field(matcher, "pattern");
            if (!pattern.matches(org.springframework.http.server.PathContainer.parsePath(item.normalizedPath()))) return false;
            Object methodMatcher = field(matcher, "method");
            var methodField = org.springframework.util.ReflectionUtils.findField(methodMatcher.getClass(), "method");
            if (methodField == null || "ANY".equals(item.httpMethod())) return true;
            org.springframework.util.ReflectionUtils.makeAccessible(methodField);
            return item.httpMethod().equals(String.valueOf(org.springframework.util.ReflectionUtils.getField(methodField, methodMatcher)));
        }
        boolean or = matcher instanceof org.springframework.security.web.util.matcher.OrRequestMatcher;
        boolean and = matcher instanceof org.springframework.security.web.util.matcher.AndRequestMatcher;
        if (or || and) {
            boolean unknown = false;
            for (Object nested : (List<?>) field(matcher, "requestMatchers")) {
                Boolean value = chainAllows(nested, item);
                if (or && Boolean.TRUE.equals(value)) return true;
                if (and && Boolean.FALSE.equals(value)) return false;
                unknown |= value == null;
            }
            return unknown ? null : and;
        }
        return null;
    }

    static String handlerIdentity(HandlerMethod handler) {
        return handler.getBeanType().getName() + "#" + handler.getMethod().getName()
                + java.util.Arrays.toString(handler.getMethod().getParameterTypes());
    }

    static String safeExpression(String expression) {
        int equals = expression.indexOf('=');
        String name = equals < 0 ? expression : expression.substring(0, equals);
        if (name.toLowerCase(java.util.Locale.ROOT).matches(".*(authorization|cookie|token|secret|password|api.?key).*"))
            return name + "=<redacted>";
        return expression;
    }

    /** The adapter reads only installed endpoint matchers, never filter credentials or settings values. */
    private static void discoverMatcher(Object matcher, String handler, List<InterfaceResourceDescriptor> descriptors) {
        if (matcher instanceof org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher) {
            var path = (org.springframework.web.util.pattern.PathPattern) field(matcher, "pattern");
            Object methodMatcher = field(matcher, "method");
            var methodField = org.springframework.util.ReflectionUtils.findField(methodMatcher.getClass(), "method");
            String method = "ANY";
            if (methodField != null && methodField.getType() == org.springframework.http.HttpMethod.class) {
                org.springframework.util.ReflectionUtils.makeAccessible(methodField);
                method = ((org.springframework.http.HttpMethod) org.springframework.util.ReflectionUtils.getField(methodField, methodMatcher)).name();
            }
            descriptors.add(new InterfaceResourceDescriptor(method, path.getPatternString(), handler, "security",
                    ResourceType.PROTOCOL, SensitiveLevel.CRITICAL, null, true,
                    "application", "SECURITY_FILTER", Map.of(), "PROTOCOL_OWNED"));
        } else if (matcher instanceof org.springframework.security.web.util.matcher.OrRequestMatcher
                || matcher instanceof org.springframework.security.web.util.matcher.AndRequestMatcher) {
            for (Object nested : (List<?>) field(matcher, "requestMatchers")) discoverMatcher(nested, handler, descriptors);
        } else if (matcher != null && matcher.getClass().isSynthetic()) {
            // Spring's discovery filters capture both single-issuer and multi-issuer matchers.
            // Select the active branch using AuthorizationServerSettings at the calling seam.
            org.springframework.util.ReflectionUtils.doWithFields(matcher.getClass(), captured -> {
                if (!org.springframework.security.web.util.matcher.RequestMatcher.class.isAssignableFrom(captured.getType())) return;
                org.springframework.util.ReflectionUtils.makeAccessible(captured);
                Object nested = org.springframework.util.ReflectionUtils.getField(captured, matcher);
                if (nested != matcher) discoverMatcher(nested, handler, descriptors);
            });
        }
    }

    private static Object field(Object target, String name) {
        var field = org.springframework.util.ReflectionUtils.findField(target.getClass(), name);
        if (field == null) throw new IllegalStateException("SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED: " + target.getClass().getName());
        org.springframework.util.ReflectionUtils.makeAccessible(field);
        return org.springframework.util.ReflectionUtils.getField(field, target);
    }

    private static ResourceType classify(String path) {
        if (path.startsWith("/oauth2/") || path.startsWith("/.well-known/") || path.equals("/login")
                || path.equals("/logout") || path.equals("/oauth2/authorize")) {
            return ResourceType.PROTOCOL;
        }
        if (path.startsWith("/actuator/") || path.startsWith("/health") || path.startsWith("/csrf")) {
            return ResourceType.INFRASTRUCTURE;
        }
        if (!path.startsWith("/api/") && !path.startsWith("/internal/")) {
            return ResourceType.STATIC;
        }
        return ResourceType.BUSINESS;
    }

    private static SensitiveLevel sensitivity(String path, String method, ResourceType type) {
        if (path.contains("token") || path.contains("authorize") || path.contains("login")
                || path.contains("logout") || "DELETE".equals(method)) {
            return SensitiveLevel.CRITICAL;
        }
        if (type != ResourceType.BUSINESS || !"GET".equals(method)) {
            return SensitiveLevel.SENSITIVE;
        }
        return SensitiveLevel.NORMAL;
    }

    private static String module(Class<?> type) {
        Package packageValue = type.getPackage();
        if (packageValue == null) {
            return "application";
        }
        String[] parts = packageValue.getName().split("\\.");
        return parts.length == 0 ? "application" : parts[parts.length - 1];
    }

    static void requirePlatformRead(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !hasClaim(jwt, "platform.interface-resources.read")
                || jwt.getAuthorities().stream().noneMatch(
                authority -> "SCOPE_platform.interface-resources.read".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Interface resource catalog denied");
        }
    }

    private static boolean hasClaim(JwtAuthenticationToken jwt, String capability) {
        Object claims = jwt.getToken().getClaims().get("platform_capabilities");
        return claims instanceof Collection<?> values && values.stream()
                .map(String::valueOf)
                .anyMatch(capability::equals);
    }
}
