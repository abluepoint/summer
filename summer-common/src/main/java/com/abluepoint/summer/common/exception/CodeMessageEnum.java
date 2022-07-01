package com.abluepoint.summer.common.exception;

public enum CodeMessageEnum implements CodeMessage {
    OK(10000,"system.ok"),

    SYSTEM_UNDEFINED_ERROR(30000,"system.undefined.error"),
    SYSTEM_CONFIG_ERROR(30001,"system.config.error"),
    VALIDATE_ERROR(21000,"validate.error"),
    VALIDATE_ERRORS(21001,"validate.errors"),
    VALIDATE_ILLEGAL_JSON_HTTP_METHOD(21002,"validate.illegal_json_http_method"),
    VALIDATE_FIELD_REQUIRED(21003,"validate.field.required"),
    VALIDATE_FIELD_ERROR(21004,"validate.field.error"),
    VALIDATE_FIELD_LIST_ILLEGAL_COUNT(21004,"validate.field.list_illegal_count"),

    SYSTEM_REQUEST_NOT_CACHED(30002,"system.request_not_cached"),
    SYSTEM_DATA_MULTI_COLUMN(31000,"system.data.multi_column"),
    ;

    private Integer code;
    private String messageKey;

    CodeMessageEnum(Integer code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessageKey() {
        return messageKey;
    }
}
