package com.abluepoint.summer.sample.dictionary.application.command;
public record DictionaryItemCommand(String value, String label, Integer sortOrder, Boolean enabled) {}
