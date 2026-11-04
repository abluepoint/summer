package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.configuration.ConfigurationActionAccess;
import com.abluepoint.summer.sample.configuration.ConfigurationOverrideService;
import com.abluepoint.summer.sample.configuration.ConfigurationTenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Current authorized names, independently of whether the values remain selectable. */
@Service
class DictionaryNameResolver {
    private final EffectiveDictionaryReader effective;
    private final ConfigurationOverrideService overrides;
    private final ConfigurationActionAccess actions;
    private final ObjectMapper mapper;

    DictionaryNameResolver(EffectiveDictionaryReader effective, ConfigurationOverrideService overrides,
                           ConfigurationActionAccess actions, ObjectMapper mapper) {
        this.effective = effective;
        this.overrides = overrides;
        this.actions = actions;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    DictionaryNameResolution resolve(String code, DictionaryNameRequest request, Authentication auth) {
        String tenant = ConfigurationTenantContext.resolve(auth);
        actions.requirePublic(tenant, "dictionaries", auth);
        var base = effective.read(tenant, code, auth, true);
        if (request == null || request.values() == null || request.values().isEmpty() || request.values().size() > 500
                || request.values().stream().anyMatch(v -> !(v instanceof String s) || s.isEmpty() || s.length() > 80)) {
            throw new SummerException(DictionaryErrorCode.INVALID,
                    List.of(new SummerException.Violation("values", "must contain 1 to 500 string values of length 1 to 80")));
        }
        // resolve() rejects hidden/disabled higher-priority overrides, before any fallback.
        var selected = overrides.resolve(tenant, "dictionaries", code, auth);
        Map<String, DictionaryItemView> labels = new HashMap<>();
        selected.ifPresent(source -> {
            List<DictionaryItemView> items = mapper.readValue(source.valueJson(),
                    mapper.getTypeFactory().constructCollectionType(List.class, DictionaryItemView.class));
            items.forEach(item -> labels.put(item.value(), item));
        });
        Map<String, DictionaryItemView> visible = new HashMap<>();
        base.snapshot().items().stream().filter(item -> base.visibleValues().contains(item.value()))
                .forEach(item -> visible.put(item.value(), item));
        List<DictionaryResolvedName> result = request.values().stream().map(value -> {
            String raw = (String) value;
            var item = visible.get(raw);
            if (item == null) return new DictionaryResolvedName(raw, null, "", "unknown");
            var overlay = labels.get(raw);
            return new DictionaryResolvedName(raw, overlay == null ? item.label() : overlay.label(), item.description(),
                    item.enabled() && (overlay == null || overlay.enabled()) ? "enabled" : "disabled");
        }).toList();
        String source = selected.map(v -> v.sourceId() + ":" + v.publishedRevision()).orElse("base");
        String token = UUID.nameUUIDFromBytes((tenant + ":" + code + ":" + base.publishedVersion() + ":"
                + base.effectiveRevision() + ":" + source + ":" + base.scopeFingerprint() + ":"
                + new TreeSet<>(base.visibleValues())).getBytes(StandardCharsets.UTF_8)).toString();
        return new DictionaryNameResolution(code, result, base.publishedVersion(), base.effectiveRevision(),
                selected.map(ConfigurationOverrideService.Value::publishedRevision).orElse(null),
                selected.isPresent() ? "override" : "base", token);
    }
}

record DictionaryNameRequest(List<Object> values) {}
record DictionaryResolvedName(String value, String label, String description, String status) {}
record DictionaryNameResolution(String code, List<DictionaryResolvedName> items, long publishedVersion,
                                long effectiveRevision, Long publishedRevision, String source, String effectiveToken) {}
