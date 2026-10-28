package com.abluepoint.summer.sample.plugin.api;


import com.abluepoint.summer.common.error.api.ErrorCategory;
import com.abluepoint.summer.common.error.api.ErrorCode;

public enum PluginErrorCode implements ErrorCode {
    INVALID("SAMPLE_PLUGIN_INVALID", "The plugin request is invalid", ErrorCategory.INVALID_ARGUMENT),
    UNTRUSTED("SAMPLE_PLUGIN_UNTRUSTED", "The plugin artifact is not trusted", ErrorCategory.CONFLICT),
    INCOMPATIBLE("SAMPLE_PLUGIN_INCOMPATIBLE", "The plugin is incompatible with the supported contract", ErrorCategory.CONFLICT);

    private final String code;
    private final String message;
    private final ErrorCategory category;

    PluginErrorCode(String code, String message, ErrorCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return message; }
    @Override public ErrorCategory category() { return category; }
}
