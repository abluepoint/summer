package com.abluepoint.summer.sample.dictionary;

import java.util.List;

record DictionaryTypeView(String code, String name, boolean published, boolean publicReadable,
                          boolean enabled, List<DictionaryItemView> items) {}

record DictionaryItemView(String value, String label, int sortOrder, boolean enabled) {}

record DictionaryTypeRequest(String code, String name, Boolean published, Boolean publicReadable,
                             Boolean enabled) {}

record DictionaryItemRequest(String value, String label, Integer sortOrder, Boolean enabled) {}

record PublicDictionaryView(String code, List<DictionaryItemView> items) {}
