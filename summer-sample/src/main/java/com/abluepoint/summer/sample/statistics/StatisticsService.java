package com.abluepoint.summer.sample.statistics;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
class StatisticsService {
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
    private static final Duration MAX_WINDOW = Duration.ofDays(366);
    private final com.abluepoint.summer.sample.resource.BusinessResourceAccess business;
    private final JdbcTemplate jdbc;
    private final AuthorizationService authorization;
    private final BusinessDataScopes dataScopes;
    private final RequestMappingHandlerMapping mappings;
    private final com.abluepoint.summer.mvc.request.RequestIdAccessor requestId;

    StatisticsService(JdbcTemplate jdbc, AuthorizationService authorization, BusinessDataScopes dataScopes,
                      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
                      com.abluepoint.summer.mvc.request.RequestIdAccessor requestId, com.abluepoint.summer.sample.resource.BusinessResourceAccess business) {
        this.business = business;
        this.jdbc = jdbc; this.authorization = authorization; this.dataScopes = dataScopes; this.mappings = mappings; this.requestId = requestId;
    }

    @Transactional(readOnly = true)
    StatisticsView read(String enterpriseId, Instant from, Instant to, String requestedOrganizationId, Authentication authentication) {
        boolean managed = business.requirePreservingDenial(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId),
                authentication, "Statistics access denied", "enterprise.statistics.read");
        if (managed) return business.readManagedHttp(
                com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId),
                authentication, "GET", "/api/v1/enterprises/{enterpriseId}/statistics",
                () -> aggregate(enterpriseId, from, to, requestedOrganizationId, authentication, true),
                "enterprise.statistics.read");
        authorization.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.statistics.read");
        return aggregate(enterpriseId, from, to, requestedOrganizationId, authentication, false);
    }

    private StatisticsView aggregate(String enterpriseId, Instant from, Instant to, String requestedOrganizationId,
                                    Authentication authentication, boolean managed) {
        Map<String, Boolean> permissions = new HashMap<>();
        java.util.function.Predicate<String> allowed = code -> permissions.computeIfAbsent(code,
                key -> managed ? sourceAllowed(enterpriseId, authentication, key) : authorization.decision(authentication, enterpriseId, key, requestId.currentRequestId()).allowed());
        if (!allowed.test("enterprise.statistics.read")) throw new AccessDeniedException("Statistics access denied");
        Instant asOf = Instant.now();
        if (to == null) to = asOf;
        if (from == null) from = to.minus(DEFAULT_WINDOW);
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("from must be before to and the window cannot exceed 366 days");
        }
        String organizationId = currentOrganization(enterpriseId, authentication);
        if (requestedOrganizationId != null && !requestedOrganizationId.equals(organizationId)) {
            throw new AccessDeniedException("Organization context mismatch");
        }
        Long users = null;
        if (allowed.test("enterprise.members.read")) {
            users = organizationId == null
                    ? count("select count(distinct subject) from summer_enterprise_member where enterprise_id = ?", enterpriseId)
                    : count("select count(distinct m.subject) from summer_enterprise_member m join summer_organization_membership o on o.enterprise_id=m.enterprise_id and o.member_id=m.member_id where m.enterprise_id=? and o.organization_id=?", enterpriseId, organizationId);
        }
        Long roles = allowed.test("enterprise.roles.read")
                ? count("select count(*) from summer_authorization_role where enterprise_id=?", enterpriseId) : null;
        Long dictionaries = null;
        if (installedAllowed(Domain.platform(), authentication, "GET", "/api/v1/platform/dictionaries", "platform.dictionary.read")) {
            dictionaries = dataScopes.read(Domain.platform().id(), "dictionaries", "read", authentication, scope ->
                    new NamedParameterJdbcTemplate(jdbc).queryForObject(
                            "select count(*) from summer_dictionary_type where " + scope.sqlPredicate(),
                            scope.sqlBindings(), Long.class));
        }
        // Task definitions have tenant ownership only; never imply an organization filter exists.
        boolean tasksAllowed = allowed.test("enterprise.tasks.read") && allowed.test("GET:/api/v1/enterprises/{enterpriseId}/tasks");
        Long tasks = tasksAllowed ? count("select count(*) from summer_task_definition where enterprise_id=?", enterpriseId) : null;
        Long executions = null, successful = null, failed = null;
        Double successRate = null;
        if (tasksAllowed) {
            executions = count("select count(*) from summer_task_execution where enterprise_id=? and started_at>=? and started_at<?", enterpriseId, from, to);
            successful = count("select count(*) from summer_task_execution where enterprise_id=? and status='SUCCESS' and started_at>=? and started_at<?", enterpriseId, from, to);
            failed = count("select count(*) from summer_task_execution where enterprise_id=? and status='FAILED' and started_at>=? and started_at<?", enterpriseId, from, to);
            successRate = executions == 0 ? 0.0 : (double) successful / executions;
        }
        Long audits = null;
        if (allowed.test("enterprise.audit.read")) {
            String sql = "select count(distinct event_id) from summer_audit_event where tenant_id=? and occurred_at>=? and occurred_at<?";
            audits = organizationId == null ? count(sql, enterpriseId, from, to)
                    : count(sql + " and organization_id=?", enterpriseId, from, to, organizationId);
        }
        long interfaces = managed ? authorizedManagedInterfaces(enterpriseId, authentication) : authorizedInterfaces(enterpriseId, allowed);
        return new StatisticsView(new StatisticsWindow(from, to), new StatisticsScope(enterpriseId, organizationId, authentication.getName()),
                users, roles, interfaces, interfaces, dictionaries, tasks, executions, successful, failed, successRate, audits, asOf);
    }

    private boolean sourceAllowed(String enterprise, Authentication actor, String source) {
        String path = switch (source) {
            case "enterprise.statistics.read" -> "/statistics";
            case "enterprise.members.read" -> "/members";
            case "enterprise.roles.read" -> "/roles";
            case "enterprise.tasks.read", "GET:/api/v1/enterprises/{enterpriseId}/tasks" -> "/tasks";
            case "enterprise.audit.read" -> "/audit-events";
            default -> null;
        };
        if (path == null) return false;
        String delegation = source.startsWith("GET:") ? "enterprise.tasks.read" : source;
        return installedAllowed(Domain.enterprise(enterprise), actor, "GET", "/api/v1/enterprises/{enterpriseId}" + path, delegation);
    }

    private boolean installedAllowed(Domain domain, Authentication actor, String method, String path, String... scopes) {
        try {
            return business.requireHttp(domain,
                    actor, method, path, scopes);
        } catch (AccessDeniedException denied) { return false; }
    }

    private long authorizedManagedInterfaces(String enterprise, Authentication actor) {
        Set<String> allowed = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            var annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ResourcePermission.class);
            if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
            String[] scopes = annotation == null || annotation.value().isBlank() ? new String[0] : new String[]{annotation.value()};
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/v1/enterprises/{enterpriseId}/") || path.contains("/statistics")) continue;
                for (var method : mapping.getMethodsCondition().getMethods())
                    if (installedAllowed(Domain.enterprise(enterprise), actor, method.name(), path, scopes)) allowed.add(method.name() + ":" + path);
            }
        });
        return allowed.size();
    }

    private String currentOrganization(String enterpriseId, Authentication authentication) {
        // Reuse scope validation, then reject a stale token/context or removed membership before aggregation.
        String organization = dataScopes.organization(enterpriseId, authentication);
        String persisted = jdbc.query("select c.organization_id from summer_organization_context c join summer_enterprise_member m on m.enterprise_id=c.enterprise_id and m.subject=c.subject and m.status='ACTIVE' join summer_organization_membership a on a.enterprise_id=m.enterprise_id and a.member_id=m.member_id and a.organization_id=c.organization_id join summer_organization_unit u on u.enterprise_id=c.enterprise_id and u.unit_id=c.organization_id and u.status='ACTIVE' where c.enterprise_id=? and c.subject=?", (rs, index) -> rs.getString(1), enterpriseId, authentication.getName()).stream().findFirst().orElse(null);
        if (!Objects.equals(organization, persisted)) throw new AccessDeniedException("Organization context is no longer active; refresh the authenticated context");
        return persisted;
    }

    private long count(String sql, Object... args) {
        for (int i = 0; i < args.length; i++) if (args[i] instanceof Instant instant) args[i] = Timestamp.from(instant);
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long authorizedInterfaces(String enterpriseId, java.util.function.Predicate<String> allowed) {
        Set<String> resources = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            var annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ResourcePermission.class);
            if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
            Set<String> methods = new HashSet<>();
            mapping.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
            if (methods.isEmpty()) methods.add("ANY");
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/") || path.contains("/statistics")) continue;
                // Global sample resources are bound to ent-acme by the request interceptor.
                if (!path.startsWith("/api/v1/enterprises/{enterpriseId}/") && !"ent-acme".equals(enterpriseId)) continue;
                for (String method : methods) {
                    String permission = annotation == null || annotation.value().isBlank() ? method + ":" + path : annotation.value();
                    if (allowed.test(permission)) resources.add(method + ":" + path);
                }
            }
        });
        return resources.size();
    }
}
