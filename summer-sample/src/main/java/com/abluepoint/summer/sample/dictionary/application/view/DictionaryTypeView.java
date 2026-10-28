package com.abluepoint.summer.sample.dictionary.application.view;

import java.util.List;

public record DictionaryTypeView(String code, String name, boolean published, boolean publicReadable,
                          boolean enabled, List<DictionaryItemView> items, long version, Long publishedVersion, String tenantId, String organizationId) {}
