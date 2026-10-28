package com.abluepoint.summer.sample.dictionary.api.request;

import com.abluepoint.summer.sample.dictionary.application.command.DictionaryItemCommand;

public record DictionaryItemRequest(String value, String label, Integer sortOrder, Boolean enabled) {
    public com.abluepoint.summer.sample.dictionary.application.command.DictionaryItemCommand toCommand() { return new com.abluepoint.summer.sample.dictionary.application.command.DictionaryItemCommand(value,label,sortOrder,enabled); }
}
