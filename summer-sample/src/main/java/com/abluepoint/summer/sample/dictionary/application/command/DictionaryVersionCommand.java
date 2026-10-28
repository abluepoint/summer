package com.abluepoint.summer.sample.dictionary.application.command;
public record DictionaryVersionCommand(Long expectedVersion, Long targetVersion, String idempotencyKey) {}
