package com.abluepoint.summer.sample.dictionary;

import java.util.List;

record DictionaryTypeView(String code, String name, boolean published, boolean publicReadable,
                          boolean enabled, List<DictionaryItemView> items, long version, Long publishedVersion, String tenantId, String organizationId) {}

record DictionaryItemView(String value, String label, int sortOrder, boolean enabled) {}

record DictionaryTypeRequest(String code, String name, Boolean published, Boolean publicReadable,
                             Boolean enabled, String organizationId) {}

record DictionaryItemRequest(String value, String label, Integer sortOrder, Boolean enabled) {}

record PublicDictionaryView(String code, List<DictionaryItemView> items, long version) {
    PublicDictionaryView(String code, List<DictionaryItemView> items) { this(code, items, 0); }
}
