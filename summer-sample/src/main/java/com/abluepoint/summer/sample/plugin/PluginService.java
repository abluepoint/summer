package com.abluepoint.summer.sample.plugin;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.customer.CustomerAuditService;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
public class PluginService {
    private static final String ENTERPRISE = "ent-acme";
    private static final String FRAMEWORK_MAJOR = "4";
    private final PluginRepository repository;
    private final PluginCatalog catalog;
    private final AuthorizationService authorization;
    private final DataScopeService dataScopes;
    private final CustomerAuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RequestIdAccessor requestId;

    PluginService(PluginRepository repository, PluginCatalog catalog, AuthorizationService authorization,
                  DataScopeService dataScopes, CustomerAuditService audit, JdbcTemplate jdbc,
                  ObjectMapper mapper, RequestIdAccessor requestId) {
        this.repository = repository; this.catalog = catalog; this.authorization = authorization;
        this.dataScopes = dataScopes; this.audit = audit; this.jdbc = jdbc; this.mapper = mapper; this.requestId = requestId;
    }

    @Transactional(readOnly = true)
    PluginPage platformList(Authentication authentication) {
        requirePlatform(authentication, "platform.plugins.read");
        List<PluginView> values = repository.findAll().stream().sorted(Comparator.comparing(PluginEntity::pluginId)).map(this::view).toList();
        return new PluginPage(values, values.stream().mapToLong(PluginView::revision).max().orElse(0));
    }

    @Transactional
    PluginView register(PluginRegisterRequest request, Authentication authentication) {
        requirePlatform(authentication, "platform.plugins.write");
        validate(request);
        PluginDescriptor descriptor = trustedDescriptor(request.pluginId(), request.artifactDigest());
        PluginStatus status = compatible(descriptor, request.version()) ? PluginStatus.REGISTERED : PluginStatus.INCOMPATIBLE;
        String diagnostic = status == PluginStatus.REGISTERED ? "Trusted artifact accepted" : incompatibleMessage(descriptor, request.version());
        PluginEntity entity = repository.findById(request.pluginId()).orElse(null);
        if (entity == null) entity = new PluginEntity(request.pluginId(), request.version(), request.artifactDigest(), status, true, diagnostic, authentication.getName());
        else entity.update(request.version(), request.artifactDigest(), status, true, diagnostic, authentication.getName());
        PluginEntity saved = repository.saveAndFlush(entity);
        audit.recordResourceSuccess("plugin:" + request.pluginId(), "PLUGIN_REGISTERED", requestId.currentRequestId(), authentication);
        return view(saved);
    }

    @Transactional
    PluginView enable(String pluginId, Authentication authentication) {
        requirePlatform(authentication, "platform.plugins.write");
        PluginEntity entity = requireEntity(pluginId);
        if (!entity.trusted()) throw new PluginTrustException("Plugin artifact is not trusted");
        PluginDescriptor descriptor = trustedDescriptor(entity.pluginId(), entity.artifactDigest());
        if (!compatible(descriptor, entity.version())) {
            entity.setStatus(PluginStatus.INCOMPATIBLE, incompatibleMessage(descriptor, entity.version()));
            repository.saveAndFlush(entity);
            throw new PluginCompatibilityException(entity.diagnostic());
        }
        entity.setStatus(PluginStatus.ENABLED, "Enabled after trust and compatibility checks");
        PluginEntity saved = repository.saveAndFlush(entity);
        audit.recordResourceSuccess("plugin:" + pluginId, "PLUGIN_ENABLED", requestId.currentRequestId(), authentication);
        return view(saved);
    }

    @Transactional
    PluginView disable(String pluginId, Authentication authentication) {
        requirePlatform(authentication, "platform.plugins.write");
        PluginEntity entity = requireEntity(pluginId);
        if (!entity.trusted()) throw new PluginTrustException("Plugin artifact is not trusted");
        entity.setStatus(PluginStatus.DISABLED, "Disabled by platform operator");
        PluginEntity saved = repository.saveAndFlush(entity);
        audit.recordResourceSuccess("plugin:" + pluginId, "PLUGIN_DISABLED", requestId.currentRequestId(), authentication);
        return view(saved);
    }

    @Transactional
    PluginView upgrade(String pluginId, PluginUpgradeRequest request, Authentication authentication) {
        requirePlatform(authentication, "platform.plugins.write");
        if (request == null || blank(request.version()) || blank(request.artifactDigest())) throw new PluginValidationException("version and artifactDigest are required");
        PluginEntity entity = requireEntity(pluginId);
        PluginDescriptor descriptor = trustedDescriptor(pluginId, request.artifactDigest());
        PluginStatus status = compatible(descriptor, request.version()) ? PluginStatus.DISABLED : PluginStatus.INCOMPATIBLE;
        String diagnostic = status == PluginStatus.DISABLED ? "Trusted upgrade is ready to enable" : incompatibleMessage(descriptor, request.version());
        entity.update(request.version(), request.artifactDigest(), status, true, diagnostic, authentication.getName());
        PluginEntity saved = repository.saveAndFlush(entity);
        audit.recordResourceSuccess("plugin:" + pluginId, "PLUGIN_UPGRADED", requestId.currentRequestId(), authentication);
        return view(saved);
    }

    @Transactional(readOnly = true)
    PluginDiagnosticView diagnostic(String pluginId, Authentication authentication) {
        requirePlatform(authentication, "platform.plugins.read");
        PluginEntity entity = requireEntity(pluginId);
        PluginDescriptor descriptor = catalog.find(pluginId);
        boolean trusted = descriptor != null && descriptor.artifactDigest().equals(entity.artifactDigest()) && entity.trusted();
        boolean compatible = trusted && compatible(descriptor, entity.version());
        List<String> problems = new ArrayList<>();
        if (!trusted) problems.add("artifact is not in the trusted catalog");
        if (!compatible) problems.add(incompatibleMessage(descriptor, entity.version()));
        return new PluginDiagnosticView(pluginId, entity.version(), trusted, compatible, entity.status(), entity.diagnostic(), List.copyOf(problems));
    }

    @Transactional(readOnly = true)
    PluginPage enterpriseList(String enterpriseId, Authentication authentication) {
        requireEnterprise(enterpriseId, authentication, "enterprise.plugins.read");
        List<PluginView> values = repository.findAll().stream().filter(entity -> entity.status() == PluginStatus.ENABLED)
                .sorted(Comparator.comparing(PluginEntity::pluginId)).map(this::view).toList();
        return new PluginPage(values, values.stream().mapToLong(PluginView::revision).max().orElse(0));
    }

    @Transactional
    PluginInsightsView customerInsights(String enterpriseId, Authentication authentication) {
        requireEnterprise(enterpriseId, authentication, "enterprise.plugins.customer-insights.read");
        requireEnterprise(enterpriseId, authentication, "customer.read");
        PluginEntity entity = repository.findById("customer-insights").orElseThrow(() -> new PluginNotFoundException("Plugin is not registered"));
        if (entity.status() != PluginStatus.ENABLED) throw new PluginCompatibilityException("Plugin is not enabled");
        dataScopes.filter(enterpriseId, "system-parameters", authentication);
        dataScopes.filter(enterpriseId, "dictionaries", authentication);
        long activeCustomers = Optional.ofNullable(jdbc.queryForObject("select count(*) from summer_customer where active = true", Long.class)).orElse(0L);
        List<Map<String, Object>> configuration = readConfiguration(entity, enterpriseId);
        PluginDictionaryView dictionary = readDictionary(enterpriseId);
        String rid = requestId.currentRequestId();
        audit.recordResourceSuccess("plugin:customer-insights", "PLUGIN_CUSTOMER_INSIGHTS_READ", rid, authentication);
        long auditRevision = Optional.ofNullable(jdbc.queryForObject("select count(*) from summer_audit_event where tenant_id = ? and resource = ?", Long.class, enterpriseId, "plugin:customer-insights")).orElse(0L);
        return new PluginInsightsView(entity.pluginId(), entity.version(), rid, activeCustomers, configuration, dictionary, auditRevision);
    }

    private List<Map<String, Object>> readConfiguration(PluginEntity entity, String enterpriseId) {
        PluginDescriptor descriptor = Objects.requireNonNull(catalog.find(entity.pluginId()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PluginConfiguration declaration : descriptor.configuration()) {
            List<Map<String, Object>> rows = jdbc.query("select parameter_key, value_type, value_json, public_flag, status from summer_system_parameter where tenant_id = ? and parameter_key = ?", (rs, row) -> {
                Map<String, Object> value = new LinkedHashMap<>(); value.put("key", rs.getString("parameter_key")); value.put("type", rs.getString("value_type")); value.put("status", rs.getString("status"));
                if (declaration.publicValue() && rs.getBoolean("public_flag")) value.put("value", parseJson(rs.getString("value_json")));
                return value;
            }, enterpriseId, declaration.key());
            if (rows.isEmpty()) result.add(Map.of("key", declaration.key(), "type", declaration.type(), "configured", false));
            else result.add(rows.get(0));
        }
        return List.copyOf(result);
    }

    private PluginDictionaryView readDictionary(String enterpriseId) {
        List<Map<String, Object>> items = jdbc.query("select i.item_value, i.label, i.sort_order from summer_dictionary_item i join summer_dictionary_type t on t.code = i.dictionary_code and t.tenant_id = i.tenant_id where i.tenant_id = ? and i.dictionary_code = ? and i.enabled = true and t.enabled = true and t.published = true order by i.sort_order, i.item_value", (rs, row) -> Map.of("value", rs.getString("item_value"), "label", rs.getString("label"), "sortOrder", rs.getInt("sort_order")), enterpriseId, "customer-segment");
        return new PluginDictionaryView("customer-segment", List.copyOf(items));
    }

    private Object parseJson(String value) {
        if (value == null) return null;
        try { return mapper.readValue(value, Object.class); } catch (JacksonException ignored) { return null; }
    }

    private PluginView enterpriseView(PluginEntity entity, String enterpriseId, Authentication authentication) {
        PluginView base = view(entity);
        List<PluginMenu> visibleMenus = base.menus().stream()
                .filter(menu -> menuAllowed(enterpriseId, menu.capabilityCode(), authentication))
                .toList();
        return new PluginView(base.pluginId(), base.displayName(), base.version(), base.artifactDigest(), base.status(),
                base.trusted(), base.diagnostic(), base.revision(), base.capabilities(), visibleMenus,
                base.configuration(), base.apiBasePath(), base.dataResources());
    }

    private boolean menuAllowed(String enterpriseId, String capability, Authentication authentication) {
        AuthorizationService.Decision decision = authorization.decision(authentication, enterpriseId, capability, requestId.currentRequestId());
        return decision.allowed() && (!"LEGACY_SCOPE".equals(decision.reasonCode())
                || authentication.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + capability).equals(a.getAuthority())));
    }

    private PluginView view(PluginEntity entity) {
        PluginDescriptor descriptor = catalog.find(entity.pluginId());
        if (descriptor == null) return new PluginView(entity.pluginId(), entity.pluginId(), entity.version(), entity.artifactDigest(), entity.status(), false, entity.diagnostic(), entity.revision(), List.of(), List.of(), List.of(), "", List.of());
        return new PluginView(entity.pluginId(), descriptor.displayName(), entity.version(), entity.artifactDigest(), entity.status(), entity.trusted(), entity.diagnostic(), entity.revision(), descriptor.capabilities(), descriptor.menus(), descriptor.configuration(), descriptor.apiBasePath(), descriptor.dataResources());
    }

    private PluginEntity requireEntity(String pluginId) { if (blank(pluginId)) throw new PluginValidationException("pluginId is required"); return repository.findById(pluginId).orElseThrow(() -> new PluginTrustException("Plugin must be registered before lifecycle actions")); }
    private PluginDescriptor trustedDescriptor(String pluginId, String digest) { PluginDescriptor descriptor = catalog.find(pluginId); if (descriptor == null || !descriptor.artifactDigest().equals(digest)) throw new PluginTrustException("Plugin artifact is not in the trusted catalog"); return descriptor; }
    private static boolean compatible(PluginDescriptor descriptor, String version) { return descriptor != null && descriptor.version().equals(version) && descriptor.requiredFrameworkVersion().startsWith(FRAMEWORK_MAJOR); }
    private static String incompatibleMessage(PluginDescriptor descriptor, String version) { return descriptor == null ? "Plugin is not registered in the trusted catalog" : "Plugin version " + version + " is incompatible with the supported contract " + descriptor.version(); }
    private static void validate(PluginRegisterRequest request) { if (request == null || blank(request.pluginId()) || !request.pluginId().matches("[a-z][a-z0-9-]{2,119}") || blank(request.version()) || blank(request.artifactDigest())) throw new PluginValidationException("pluginId, version and artifactDigest are required"); }
    private static void requirePlatform(Authentication authentication, String capability) { if (!(authentication instanceof JwtAuthenticationToken jwt) || !hasClaim(jwt, capability) || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + capability).equals(a.getAuthority()))) throw new AccessDeniedException("Platform plugin capability denied"); }
    private void requireEnterprise(String enterpriseId, Authentication authentication, String capability) { authorization.requireEnterpriseMembership(enterpriseId, authentication); AuthorizationService.Decision decision = authorization.decision(authentication, enterpriseId, capability, requestId.currentRequestId()); if (!decision.allowed() || ("LEGACY_SCOPE".equals(decision.reasonCode()) && authentication.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + capability).equals(a.getAuthority())))) throw new AccessDeniedException("Plugin capability denied"); }
    private static boolean hasClaim(JwtAuthenticationToken jwt, String capability) { Object values = jwt.getToken().getClaims().get("platform_capabilities"); return values instanceof Collection<?> c && c.stream().map(String::valueOf).anyMatch(capability::equals); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
