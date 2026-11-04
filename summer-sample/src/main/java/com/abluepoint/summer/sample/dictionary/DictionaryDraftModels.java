package com.abluepoint.summer.sample.dictionary;

import java.util.List;

record DictionaryDraftItem(String id, Object value, String label, String description, boolean enabled, Boolean everPublished) {}
record DictionaryDraftRequest(Object expectedDraftRevision, String name, Boolean publicReadable, Boolean enabled,
                              List<DictionaryDraftItem> items, List<String> itemOrder) {}
record DictionaryDraftView(String code, String name, boolean publicReadable, boolean enabled, long draftRevision,
                           long publishedVersion, List<DictionaryDraftItem> items, List<String> itemOrder) {}
