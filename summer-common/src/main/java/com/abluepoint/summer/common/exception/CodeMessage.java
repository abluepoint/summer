package com.abluepoint.summer.common.exception;

public interface CodeMessage {
    public Integer getCode();
    public String getMessageKey();

    public static CodeMessage of(Integer code, String messageKey) {
        return new CodeMessageDefault(code, messageKey);
    }

}
