package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.ErrorCategory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Exposes only currently authorized options from the effective published snapshot. */
@Service
public class PublishedDictionaryReader {
    private final DictionaryVersionService dictionaries;
    PublishedDictionaryReader(DictionaryVersionService dictionaries) { this.dictionaries = dictionaries; }
    public List<Map<String, Object>> read(String code, Authentication auth) { return read(com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve(auth), code, auth); }
    public List<Map<String, Object>> read(String tenant, String code, Authentication auth) {
        com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.requireMembership(tenant, auth);
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
