package com.abluepoint.summer.mvc.domain;

import com.abluepoint.summer.common.util.AppContextUtil;

import java.util.Locale;

public class R {

    private static Locale getDefaultLocale() {
        return Locale.SIMPLIFIED_CHINESE;
    }

    public static <T> Result<T> ok() {
        return ok(null, ReturnCode.OK.message(), null);
    }

    public static <T> Result<T> ok(T data) {
        return ok(data, ReturnCode.OK.message());
    }


    public static <T> Result<T> ok(T data, String msgCode, Object... args) {
        String message = AppContextUtil.getAppContext().getMessage(msgCode, args, getDefaultLocale());
        return Results.success(message, data);
    }

    public static <T> Result<T> ok(T data, int code, String msgCode, Object... args) {
        String message = AppContextUtil.getAppContext().getMessage(msgCode, args, getDefaultLocale());
        return Results.success(code, message, data);
    }

    public static <T> Result<T> fail(T data, int code, String msgCode, Object... args) {
        String message = AppContextUtil.getAppContext().getMessage(msgCode, args, getDefaultLocale());
        return Results.fail(code, message, data);
    }

    public static <T> Result<T> fail(int code, String msgCode, Object... args) {
        return fail(null, code, msgCode, args);
    }
}
