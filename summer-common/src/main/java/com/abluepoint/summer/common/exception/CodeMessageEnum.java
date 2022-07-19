package com.abluepoint.summer.common.exception;

public enum CodeMessageEnum implements CodeMessage {
    OK(10000,"system.message.ok"),
    PENDING(10001, "system.message.pending"),

    LOGIN_FAILED(20001,"system.message.login_failed"),
    UNAUTHORIZED(20002,"system.message.unauthorized"),
    FORBIDDEN(20003, "system.message.forbidden"),

    SYSTEM_UNDEFINED_ERROR(40000,"system.undefined.error"),
    SYSTEM_CONFIG_ERROR(40001,"system.config.error"),

    VALIDATE_ERROR(31000,"validate.error"),
    VALIDATE_ERRORS(31001,"validate.errors"),
    VALIDATE_ILLEGAL_JSON_HTTP_METHOD(31002,"validate.illegal_json_http_method"),
    VALIDATE_FIELD_REQUIRED(31003,"validate.field.required"),
    VALIDATE_FIELD_ERROR(31004,"validate.field.error"),
    VALIDATE_FIELD_LIST_ILLEGAL_COUNT(31004,"validate.field.list_illegal_count"),

    SYSTEM_REQUEST_NOT_CACHED(40002,"system.request_not_cached"),
    SYSTEM_DATA_MULTI_COLUMN(41000,"system.data.multi_column"),
    SYSTEM_DATA_LOGIC_DELETE_NOT_SUPPORT(41003,"system.data.logic_delete_not_support"),

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
