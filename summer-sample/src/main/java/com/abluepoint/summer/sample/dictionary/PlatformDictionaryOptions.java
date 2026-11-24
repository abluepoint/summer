package com.abluepoint.summer.sample.dictionary;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** The published platform vocabulary exposed to authorized catalog consumers. */
@Service
public class PlatformDictionaryOptions {
    public static final String MENU_CLIENT = "menu-client";
    public static final String WEB_CLIENT = "web";
    public record Option(String value, String label, boolean enabled) {}
    private final DictionaryTypeRepository types;
    private final DictionaryVersionRepository versions;
    private final ObjectMapper json;

    PlatformDictionaryOptions(DictionaryTypeRepository types, DictionaryVersionRepository versions, ObjectMapper json) {
        this.types = types;
        this.versions = versions;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Option> published(String code) {
        return types.findByTenantIdAndCode("platform", code)
                .filter(type -> type.published() && type.publishedVersion() != null)
                .flatMap(type -> versions.findByTenantIdAndDictionaryCodeAndVersion("platform", code, type.publishedVersion()))
                .map(row -> json.readValue(row.snapshotJson(), DictionarySnapshot.class))
                .filter(snapshot -> snapshot.publicReadable() && snapshot.enabled())
                .map(snapshot -> snapshot.items().stream()
                        .map(item -> new Option(item.value(), item.label(), item.enabled())).toList())
                .orElse(List.of());
    }
}
