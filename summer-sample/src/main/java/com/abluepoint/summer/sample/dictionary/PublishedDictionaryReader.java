package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext;

import com.abluepoint.summer.common.error.api.SummerException;
import com.abluepoint.summer.common.error.api.ErrorCategory;
import com.abluepoint.summer.common.error.api.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Exposes only currently authorized options from the effective published snapshot. */
@Service
public class PublishedDictionaryReader {
    private final DictionaryVersionService dictionaries;
    PublishedDictionaryReader(DictionaryVersionService dictionaries) { this.dictionaries = dictionaries; }
    DictionaryItemView readItem(String code, String value, Authentication auth) {
        PublicDictionaryView published = dictionaries.publicRead(code, auth);
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,78}[a-z0-9]")) {
            throw new SummerException(DictionaryErrorCode.INVALID,
                    List.of(new SummerException.Violation("value", "is invalid")));
        }
        return published.items().stream()
                .filter(item -> item.enabled() && value.equals(item.value()))
                .findFirst().orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
    }
    public List<Map<String, Object>> read(String code, Authentication auth) { return read(com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve(auth), code, auth); }
    public List<Map<String, Object>> read(String tenant, String code, Authentication auth) {
        com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.requireMembership(tenant, auth);
        try {
            return dictionaries.publicRead(tenant, code, auth).items().stream()
                    .map(item -> Map.<String, Object>of("value", item.value(), "label", item.label(), "sortOrder", item.sortOrder()))
                    .toList();
        } catch (SummerException failure) {
            if (failure.errorCode().category() == ErrorCategory.NOT_FOUND) return List.of();
            throw failure;
        }
    }
}
