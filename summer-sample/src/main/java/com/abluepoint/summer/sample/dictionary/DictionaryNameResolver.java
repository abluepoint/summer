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
        return resolve(code, request, auth, false);
    }

    @Transactional(readOnly = true)
    DictionaryNameResolution selection(String code, DictionaryNameRequest request, Authentication auth) {
        return resolve(code, request, auth, true);
    }

    private DictionaryNameResolution resolve(String code, DictionaryNameRequest request, Authentication auth, boolean selection) {
        String tenant = ConfigurationTenantContext.resolve(auth);
        actions.requirePublic(tenant, "dictionaries", auth);
        var base = effective.read(tenant, code, auth, true);
        if (request == null || request.values() == null || (!selection && request.values().isEmpty()) || request.values().size() > 500
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
        Set<String> baseValues = base.snapshot().items().stream().map(DictionaryItemView::value).collect(java.util.stream.Collectors.toSet());
        boolean overrideReviewRequired = labels.keySet().stream().anyMatch(value -> !baseValues.contains(value));
        Map<String, DictionaryChoiceFacet> facets = new LinkedHashMap<>();
        if (selection) {
            List<String> requestedGroups = request.groupCodes() == null ? List.of(request.groupCode() == null ? "" : request.groupCode()) : request.groupCodes();
            if (requestedGroups.size() > 200 || requestedGroups.stream().anyMatch(Objects::isNull))
                throw new SummerException(DictionaryErrorCode.INVALID);
            Set<String> allowed = base.selectableValues();
            List<DictionaryItemView> candidates = selected.isPresent()
                    ? mapper.readValue(selected.get().valueJson(), mapper.getTypeFactory().constructCollectionType(List.class, DictionaryItemView.class))
                    : base.snapshot().items();
            List<DictionaryItemView> choices = candidates.stream().filter(DictionaryItemView::enabled).filter(i -> allowed.contains(i.value()))
                    .map(i -> new DictionaryItemView(i.value(), i.label(), i.sortOrder(), true, visible.get(i.value()).description())).toList();
            Map<String, DictionaryItemView> byValue = new HashMap<>();
            choices.forEach(i -> byValue.put(i.value(), i));
            for (String groupCode : requestedGroups) {
                if (groupCode.isEmpty()) { facets.put(groupCode, new DictionaryChoiceFacet(choices, "available")); continue; }
                var group = base.snapshot().groups().stream().filter(g -> g.code().equals(groupCode) && g.enabled()).findFirst();
                facets.put(groupCode, group.isEmpty() ? new DictionaryChoiceFacet(List.of(), "unavailable")
                        : new DictionaryChoiceFacet(group.get().memberValues().stream().filter(byValue::containsKey).map(byValue::get).toList(), "available"));
            }
        }
        var primary = facets.getOrDefault(request.groupCode() == null ? "" : request.groupCode(), new DictionaryChoiceFacet(List.of(), "available"));
        String source = selected.map(v -> v.sourceId() + ":" + v.publishedRevision()).orElse("base");
        String token = UUID.nameUUIDFromBytes((tenant + ":" + code + ":" + base.publishedVersion() + ":"
                + base.effectiveRevision() + ":" + source + ":" + base.scopeFingerprint() + ":"
                + new TreeSet<>(base.visibleValues())).getBytes(StandardCharsets.UTF_8)).toString();
        return new DictionaryNameResolution(code, result, base.publishedVersion(), base.effectiveRevision(),
                selected.map(ConfigurationOverrideService.Value::publishedRevision).orElse(null),
                selected.isPresent() ? "override" : "base", token, primary.choices(), primary.groupStatus(), facets, overrideReviewRequired);
    }
}

record DictionaryNameRequest(List<Object> values, String groupCode, List<String> groupCodes) {}
record DictionaryChoiceFacet(List<DictionaryItemView> choices, String groupStatus) {}
record DictionaryResolvedName(String value, String label, String description, String status) {}
record DictionaryNameResolution(String code, List<DictionaryResolvedName> items, long publishedVersion,
                                long effectiveRevision, Long publishedRevision, String source, String effectiveToken, List<DictionaryItemView> choices, String groupStatus, Map<String, DictionaryChoiceFacet> facets, boolean overrideReviewRequired) {}
