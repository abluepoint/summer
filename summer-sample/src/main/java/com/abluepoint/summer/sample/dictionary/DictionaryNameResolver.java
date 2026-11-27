package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.configuration.ConfigurationActionAccess;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Current authorized names, independently of whether the values remain selectable. */
@Service
class DictionaryNameResolver {
    private final EffectiveDictionaryReader effective;
    private final ConfigurationActionAccess actions;

    DictionaryNameResolver(EffectiveDictionaryReader effective, ConfigurationActionAccess actions) {
        this.effective = effective;
        this.actions = actions;
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
        String tenant = "platform";
        actions.requirePublic(tenant, "dictionaries", auth);
        var base = effective.read(tenant, code, auth, true);
        if (request == null || request.values() == null || (!selection && request.values().isEmpty()) || request.values().size() > 500
                || request.values().stream().anyMatch(v -> !(v instanceof String s) || s.isEmpty() || s.length() > 80)) {
            throw new SummerException(DictionaryErrorCode.INVALID,
                    List.of(new SummerException.Violation("values", "must contain 1 to 500 string values of length 1 to 80")));
        }
        Map<String, DictionaryItemView> visible = new HashMap<>();
        base.snapshot().items().stream().filter(item -> base.visibleValues().contains(item.value()))
                .forEach(item -> visible.put(item.value(), item));
        List<DictionaryResolvedName> result = request.values().stream().map(value -> {
            String raw = (String) value;
            var item = visible.get(raw);
            if (item == null) return new DictionaryResolvedName(raw, null, "", "unknown");
            return new DictionaryResolvedName(raw, item.label(), item.description(), item.enabled() ? "enabled" : "disabled");
        }).toList();
        Map<String, DictionaryChoiceFacet> facets = new LinkedHashMap<>();
        if (selection) {
            List<String> requestedGroups = request.groupCodes() == null ? List.of(request.groupCode() == null ? "" : request.groupCode()) : request.groupCodes();
            if (requestedGroups.size() > 200 || requestedGroups.stream().anyMatch(Objects::isNull))
                throw new SummerException(DictionaryErrorCode.INVALID);
            Set<String> allowed = base.selectableValues();
            List<DictionaryItemView> choices = base.snapshot().items().stream().filter(i -> allowed.contains(i.value())).toList();
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
        String source = "base";
        String token = UUID.nameUUIDFromBytes((tenant + ":" + code + ":" + base.publishedVersion() + ":"
                + base.effectiveRevision() + ":" + source + ":" + base.scopeFingerprint() + ":"
                + new TreeSet<>(base.visibleValues())).getBytes(StandardCharsets.UTF_8)).toString();
        return new DictionaryNameResolution(code, result, base.publishedVersion(), base.effectiveRevision(),
                null,
                source, token, primary.choices(), primary.groupStatus(), facets, false);
    }
}

record DictionaryNameRequest(List<Object> values, String groupCode, List<String> groupCodes) {}
record DictionaryChoiceFacet(List<DictionaryItemView> choices, String groupStatus) {}
record DictionaryResolvedName(String value, String label, String description, String status) {}
record DictionaryNameResolution(String code, List<DictionaryResolvedName> items, long publishedVersion,
                                long effectiveRevision, Long publishedRevision, String source, String effectiveToken, List<DictionaryItemView> choices, String groupStatus, Map<String, DictionaryChoiceFacet> facets, boolean overrideReviewRequired) {}
