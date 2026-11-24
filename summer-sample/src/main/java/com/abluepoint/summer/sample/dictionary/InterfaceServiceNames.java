package com.abluepoint.summer.sample.dictionary;

import java.util.List;
import org.springframework.stereotype.Service;

/** The published platform vocabulary exposed to authorized interface catalog consumers. */
@Service
public class InterfaceServiceNames {
    public static final String CODE = "interface-service-name";
    public record Option(String value, String label, boolean enabled) {}
    private final PlatformDictionaryOptions dictionary;

    InterfaceServiceNames(PlatformDictionaryOptions dictionary) { this.dictionary = dictionary; }

    public List<Option> published() {
        return dictionary.published(CODE).stream()
                .map(item -> new Option(item.value(), item.label(), item.enabled())).toList();
    }
}
