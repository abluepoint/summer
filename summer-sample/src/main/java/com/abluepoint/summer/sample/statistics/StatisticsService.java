package com.abluepoint.summer.sample.statistics;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.abluepoint.summer.sample.resource.ResourcePermission;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
final class StatisticsService {
    private static final String STATISTICS_READ = "enterprise.statistics.read";
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
    private static final Duration MAX_WINDOW = Duration.ofDays(366);

    private final JdbcTemplate jdbc;
    private final AuthorizationService authorization;
    private final RequestMappingHandlerMapping mappings;

    StatisticsService(JdbcTemplate jdbc, AuthorizationService authorization, RequestMappingHandlerMapping mappings) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.mappings = mappings;
    }

    @Transactional(readOnly = true)
    StatisticsView read(String enterpriseId, Instant from, Instant to, String requestedOrganizationId,
                        Authentication authentication) {
        authorization.requireEnterpriseMenu(enterpriseId, authentication, STATISTICS_READ);
        if (from == null) from = Instant.now().minus(DEFAULT_WINDOW);
        if (to == null) to = Instant.now();
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("from must be before to and the window cannot exceed 366 days");
        }
        String organizationId = organizationId(authentication);
        if (requestedOrganizationId != null && !requestedOrganizationId.isBlank()) {
            if (organizationId == null || !requestedOrganizationId.equals(organizationId)) {
                throw new AccessDeniedException("Organization context mismatch");
            }
            organizationId = requestedOrganizationId;
        }

        long users = count("select count(distinct username) from summer_authorization_user_binding where enterprise_id = ?", enterpriseId);
        long roles = count("select count(*) from summer_authorization_role where enterprise_id = ?", enterpriseId);
        long dictionaries = organizationId == null
                ? count("select count(*) from summer_dictionary_type where tenant_id = ?", enterpriseId)
                : count("select count(*) from summer_dictionary_type where tenant_id = ? and organization_id = ?", enterpriseId, organizationId);
        long tasks = count("select count(*) from summer_task_definition where enterprise_id = ?", enterpriseId);
        long executions = count("select count(*) from summer_task_execution where enterprise_id = ? and started_at >= ? and started_at < ?", enterpriseId, from, to);
        long successful = count("select count(*) from summer_task_execution where enterprise_id = ? and status = 'SUCCESS' and started_at >= ? and started_at < ?", enterpriseId, from, to);
        long failed = count("select count(*) from summer_task_execution where enterprise_id = ? and status = 'FAILED' and started_at >= ? and started_at < ?", enterpriseId, from, to);
        long audits = count("select count(distinct event_id) from aict_audit_event where enterprise_id = ? and occurred_at >= ? and occurred_at < ?", enterpriseId, from, to);
        long interfaces = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof HandlerMethod)
                .mapToLong(entry -> businessMappingCount(entry.getKey()))
                .sum();
        long accessible = accessibleInterfaces(enterpriseId, authentication);
        double successRate = executions == 0 ? 0.0d : (double) successful / executions;
        return new StatisticsView(new StatisticsWindow(from, to),
                new StatisticsScope(enterpriseId, organizationId, authentication.getName()), users, roles,
                interfaces, accessible, dictionaries, tasks,
                executions, successful, failed, successRate, audits);
    }

    private long count(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private long accessibleInterfaces(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return 0L;
        Object claim = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(claim instanceof Map<?, ?> capabilityMap) || !(capabilityMap.get(enterpriseId) instanceof java.util.Collection<?> values)) return 0L;
        Set<String> permissions = new HashSet<>();
        values.forEach(value -> permissions.add(String.valueOf(value)));
        return mappings.getHandlerMethods().entrySet().stream()
                .mapToLong(entry -> accessibleMappingCount(entry.getKey(), entry.getValue(), permissions))
                .sum();
    }

    private static long accessibleMappingCount(RequestMappingInfo mapping, HandlerMethod handler, Set<String> permissions) {
        long total = 0L;
        for (String path : mapping.getPatternValues()) {
            if (!path.startsWith("/api/") || path.contains("/statistics")) continue;
            ResourcePermission annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ResourcePermission.class);
            if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
            Set<String> methods = mapping.getMethodsCondition().getMethods().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
            if (methods.isEmpty()) methods = Set.of("ANY");
            for (String method : methods) {
                String permission = annotation == null || annotation.value().isBlank() ? method + ":" + path : annotation.value();
                if (permissions.contains(permission)) total++;
            }
        }
        return total;
    }

    private static long businessMappingCount(RequestMappingInfo mapping) {
        return mapping.getPatternValues().stream().filter(path -> path.startsWith("/api/"))
                .filter(path -> !path.contains("/statistics"))
                .mapToLong(path -> 1L).sum();
    }

    private static String organizationId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return null;
        Map<String, Object> claims = jwt.getToken().getClaims();
        for (String key : List.of("organization_id", "organizationId", "current_organization_id")) {
            Object value = claims.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        Object context = claims.get("organization_context");
        if (context instanceof Map<?, ?> map) {
            Object value = map.get("organizationId");
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }
}
