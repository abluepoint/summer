package com.abluepoint.summer.mvc.domain;

import com.abluepoint.summer.common.util.AppContextUtil;
import org.slf4j.Logger;

import java.util.Locale;

public class R {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(R.class);

    private static Locale getDefaultLocale() {
        return Locale.SIMPLIFIED_CHINESE;
    }

    public static <T> Result<T> ok() {
        return ok(null, ReturnCode.OK.message(), null);
    }

    public static <T> Result<T> ok(T data) {
        return ok(data, ReturnCode.OK.message());
    }


    public static <T> Result<T> ok(T data, String messageKey, Object... args) {
        String message = AppContextUtil.getAppContext().getMessage(messageKey, args, getDefaultLocale());
        Integer rcCode = getRcCode(messageKey);
        if(rcCode==null){
            rcCode = -1;
        }
        return Results.success(rcCode, message, data);
    }

    public static <T> Result<T> fail(T data, String messageKey, Object... args) {
        String message = AppContextUtil.getAppContext().getMessage(messageKey, args, getDefaultLocale());
        Integer rcCode = getRcCode(messageKey);
        if(rcCode==null){
            rcCode = -1;
        }
        return Results.fail(rcCode, message, data);
    }

    public static Integer getRcCode(String messageKey){
        int dotIndex = messageKey.indexOf(".");
        if (dotIndex != -1) {
            String rcCodeStr = messageKey.substring(0, dotIndex);
            try {
                Integer rcCode = Integer.valueOf(rcCodeStr);
                return rcCode;
            } catch (Exception e) {
                log.error("messageKey must start with code", e);
            }
        }
        return null;
    }

    public static <T> Result<T> fail(String messageKey, Object... args) {
        return fail(null, messageKey, args);
    }
}
