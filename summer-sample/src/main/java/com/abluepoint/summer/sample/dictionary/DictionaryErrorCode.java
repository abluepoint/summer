package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

enum DictionaryErrorCode implements ErrorCode {
    INVALID("SUMMER_DICTIONARY_INVALID", "The dictionary request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_DICTIONARY_CONFLICT", "The dictionary request conflicts with current state", ErrorCategory.CONFLICT),
    VERSION_CONFLICT("SUMMER_DICTIONARY_VERSION_CONFLICT", "The dictionary version is stale", ErrorCategory.CONFLICT),
    INVALID_STATE("SUMMER_DICTIONARY_INVALID_STATE", "The dictionary state transition is invalid", ErrorCategory.CONFLICT);

    private final String code;
    private final String message;
    private final ErrorCategory category;

    DictionaryErrorCode(String code, String message, ErrorCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return message; }
    @Override public ErrorCategory category() { return category; }
}
