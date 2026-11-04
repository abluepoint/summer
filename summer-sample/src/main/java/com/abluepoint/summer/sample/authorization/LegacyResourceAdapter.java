package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bridges actual MVC mappings and explicitly registered non-MVC resources to the legacy engine. */
@Service
public class LegacyResourceAdapter {
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final JdbcTemplate jdbc;
    LegacyResourceAdapter(ObjectProvider<RequestMappingHandlerMapping> mappings, JdbcTemplate jdbc) {
        this.mappings = mappings; this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resource resolve(HttpServletRequest request, HandlerMethod handler) {
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = matched instanceof PathPattern p ? p.getPatternString() : matched instanceof String s ? s : null;
        if (path == null) throw new AccessDeniedException("Matched resource mapping is unavailable");
        var candidates = mappings.orderedStream().flatMap(mapping -> mapping.getHandlerMethods().entrySet().stream())
                .filter(entry -> entry.getValue().getMethod().equals(handler.getMethod())
                        && entry.getValue().getBeanType().equals(handler.getBeanType())
                        && entry.getKey().getPatternValues().contains(path)
                        && entry.getKey().getMatchingCondition(request) != null).toList();
        if (candidates.size() != 1) throw new AccessDeniedException("Resource mapping is ambiguous or unsupported");
        var mapping = candidates.getFirst().getKey();
        if (mapping.getCustomCondition() != null) throw new AccessDeniedException("Custom mapping requires an explicit resource adapter");
        Map<String, List<String>> conditions = new TreeMap<>();
        conditions.put("params", mapping.getParamsCondition().getExpressions().stream().map(Object::toString).toList());
        conditions.put("headers", mapping.getHeadersCondition().getExpressions().stream().map(Object::toString).toList());
        conditions.put("consumes", mapping.getConsumesCondition().getExpressions().stream().map(Object::toString).toList());
        conditions.put("produces", mapping.getProducesCondition().getExpressions().stream().map(Object::toString).toList());
        conditions.put("version", List.of(mapping.getVersionCondition().toString()));
        var explicit = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ResourcePermission.class);
        if (explicit == null) explicit = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
        String normalized = path.trim().replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        String code = explicit == null || explicit.value().isBlank()
                ? request.getMethod().toUpperCase(Locale.ROOT) + ":" + normalized : explicit.value().trim();
        return register("application", "MVC", request.getMethod(), normalized, conditions, code);
    }

    /** Called only by trusted extension wiring; request parameters must never supply these values. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resource register(String application, String source, String method, String path,
                             Map<String, List<String>> conditions, String legacyCode) {
        StringBuilder locator = new StringBuilder();
        for (String value : List.of(application, source, method.toUpperCase(Locale.ROOT), path)) frame(locator, value);
        new TreeMap<>(conditions).forEach((key, values) -> {
            frame(locator, key); frame(locator, Integer.toString(values.size()));
            values.stream().distinct().sorted().forEach(value -> frame(locator, value));
        });
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(locator.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        String id = "interface-" + hash;
        jdbc.update("insert into summer_authorization_resource_identity(resource_id, locator_hash, legacy_code) values (?, ?, ?) "
                + "on duplicate key update locator_hash = locator_hash", id, hash, legacyCode);
        String registered = permission(new Resource(id, "INTERFACE", source));
        if (!Objects.equals(registered, legacyCode)) throw new AccessDeniedException("Legacy resource binding changed");
        return new Resource(id, "INTERFACE", source);
    }
    String permission(Resource resource) {
        if (!"INTERFACE".equals(resource.type())) return null;
        var rows = jdbc.query("select legacy_code from summer_authorization_resource_identity where resource_id = ?",
                (rs, n) -> rs.getString(1), resource.id());
        return rows.size() == 1 ? rows.getFirst() : null;
    }
    private static void frame(StringBuilder out, String value) { out.append(value.length()).append(':').append(value); }
}
