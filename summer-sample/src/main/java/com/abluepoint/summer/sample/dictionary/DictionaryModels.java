package com.abluepoint.summer.sample.dictionary;

import java.util.List;

record DictionaryTypeView(String code, String name, boolean published, boolean publicReadable,
                          boolean enabled, List<DictionaryItemView> items, long version, Long publishedVersion, String tenantId, String organizationId) {}

record DictionaryItemView(String value, String label, int sortOrder, boolean enabled, String description) {
    DictionaryItemView { description = description == null ? "" : description; }
    DictionaryItemView(String value, String label, int sortOrder, boolean enabled) { this(value, label, sortOrder, enabled, ""); }
}

record DictionaryTypeRequest(String code, String name, Boolean published, Boolean publicReadable,
                             Boolean enabled, String organizationId) {}

record DictionaryItemRequest(String value, String label, Integer sortOrder, Boolean enabled) {}

record PublicDictionaryView(String code, List<DictionaryItemView> items, long version, long publishedVersion,
                            Long publishedRevision, String source, String effectiveToken) {
    PublicDictionaryView(String code, List<DictionaryItemView> items, long version) { this(code, items, version, version, null, "base", version + ":base"); }
    PublicDictionaryView(String code, List<DictionaryItemView> items) { this(code, items, 0); }
}
