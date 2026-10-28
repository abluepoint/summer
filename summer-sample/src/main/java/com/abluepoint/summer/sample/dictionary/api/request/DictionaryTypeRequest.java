package com.abluepoint.summer.sample.dictionary.api.request;

import com.abluepoint.summer.sample.dictionary.application.command.DictionaryTypeCommand;

public record DictionaryTypeRequest(String code, String name, Boolean published, Boolean publicReadable,
                             Boolean enabled, String organizationId) {
    public com.abluepoint.summer.sample.dictionary.application.command.DictionaryTypeCommand toCommand() { return new com.abluepoint.summer.sample.dictionary.application.command.DictionaryTypeCommand(code,name,published,publicReadable,enabled,organizationId); }
}
