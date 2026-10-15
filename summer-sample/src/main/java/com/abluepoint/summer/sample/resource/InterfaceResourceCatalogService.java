package com.abluepoint.summer.sample.resource;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
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
final class InterfaceResourceCatalogService implements SmartInitializingSingleton {

    private final RequestMappingHandlerMapping handlerMapping;
    private volatile InterfaceResourceCatalog catalog = new InterfaceResourceCatalog(List.of());

    InterfaceResourceCatalogService(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<InterfaceResourceDescriptor> descriptors = new ArrayList<>();
        Set<String> frameworkDuplicateKeys = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Collection<String> paths = mapping.getPatternValues();
            Collection<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            if (paths.isEmpty()) {
                paths = List.of("/");
            }
            for (String path : paths) {
                if (methods.isEmpty()) {
                    addDescriptor(descriptors, frameworkDuplicateKeys, descriptor(path, null, handler));
                } else {
                    for (RequestMethod method : methods) {
                        addDescriptor(descriptors, frameworkDuplicateKeys, descriptor(path, method, handler));
                    }
                }
            }
        }
        Set<String> mappedKeys = new HashSet<>();
        descriptors.forEach(descriptor -> mappedKeys.add(descriptor.key()));
        infrastructureResources().stream()
                .filter(descriptor -> !mappedKeys.contains(descriptor.key()))
                .forEach(descriptors::add);
        catalog = new InterfaceResourceCatalog(descriptors);
    }

    private static void addDescriptor(
            List<InterfaceResourceDescriptor> descriptors,
            Set<String> frameworkDuplicateKeys,
            InterfaceResourceDescriptor descriptor
    ) {
        if ("/error".equals(descriptor.normalizedPath())
                && !frameworkDuplicateKeys.add(descriptor.key())) {
            return;
        }
        descriptors.add(descriptor);
    }

    InterfaceResourceCatalogView read(Authentication authentication) {
        requirePlatformRead(authentication);
        return new InterfaceResourceCatalogView(catalog.items(), catalog.revision());
    }

    InterfaceResourceCatalog snapshot() {
        return catalog;
    }

    private static InterfaceResourceDescriptor descriptor(String path, RequestMethod method, HandlerMethod handler) {
        String httpMethod = method == null ? "ANY" : method.name();
        Method methodRef = handler.getMethod();
        ResourcePermission explicit = AnnotatedElementUtils.findMergedAnnotation(methodRef, ResourcePermission.class);
        if (explicit == null) {
            explicit = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
        }
        ResourceType type = classify(path);
        SensitiveLevel sensitivity = sensitivity(path, httpMethod, type);
        String module = module(handler.getBeanType());
        return InterfaceResourceDescriptor.mvc(
                httpMethod,
                path,
                handler.getBeanType().getSimpleName() + "#" + methodRef.getName(),
                module,
                type,
                sensitivity,
                explicit == null ? null : explicit.value(),
                true
        );
    }

    private static List<InterfaceResourceDescriptor> infrastructureResources() {
        return List.of(
                InterfaceResourceDescriptor.mvc("GET", "/oauth2/authorize", "OAuth2AuthorizationEndpoint#authorize", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.CRITICAL, null, true),
                InterfaceResourceDescriptor.mvc("POST", "/oauth2/token", "OAuth2AuthorizationEndpoint#token", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.CRITICAL, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/oauth2/jwks", "NimbusJwkSetEndpoint#keys", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/userinfo", "OidcUserInfoEndpoint#userinfo", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/.well-known/oauth-authorization-server", "OAuth2AuthorizationServerMetadataEndpoint#metadata", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/.well-known/openid-configuration", "OidcProviderConfigurationEndpoint#configuration", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/login", "LoginPage#login", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("POST", "/login", "UsernamePasswordAuthenticationFilter#attemptAuthentication", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.CRITICAL, null, true),
                InterfaceResourceDescriptor.mvc("POST", "/logout", "LogoutFilter#logout", "security",
                        ResourceType.PROTOCOL, SensitiveLevel.CRITICAL, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/csrf", "SummerCsrfEndpoint#csrf", "security",
                        ResourceType.INFRASTRUCTURE, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/actuator/health", "HealthEndpointWebExtension#health", "actuator",
                        ResourceType.INFRASTRUCTURE, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/actuator/info", "InfoEndpointWebExtension#info", "actuator",
                        ResourceType.INFRASTRUCTURE, SensitiveLevel.NORMAL, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/actuator/metrics", "MetricsEndpoint#listNames", "actuator",
                        ResourceType.INFRASTRUCTURE, SensitiveLevel.SENSITIVE, null, true),
                InterfaceResourceDescriptor.mvc("GET", "/", "ResourceHttpRequestHandler#handleRequest", "web",
                        ResourceType.STATIC, SensitiveLevel.NORMAL, null, true)
        );
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

    private static void requirePlatformRead(Authentication authentication) {
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
