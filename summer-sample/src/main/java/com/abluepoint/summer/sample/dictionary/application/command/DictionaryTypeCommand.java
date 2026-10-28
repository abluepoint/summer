package com.abluepoint.summer.sample.dictionary.application.command;
public record DictionaryTypeCommand(String code, String name, Boolean published, Boolean publicReadable, Boolean enabled, String organizationId) {}
