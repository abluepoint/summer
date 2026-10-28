package com.abluepoint.summer.sample.dictionary.application.view;

import java.util.List;

public record PublicDictionaryView(String code, List<DictionaryItemView> items, long version) {
    public PublicDictionaryView(String code, List<DictionaryItemView> items) { this(code, items, 0); }
}
