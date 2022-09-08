package com.abluepoint.summer.mvc.domain;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.CodeMessageDefault;
import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.common.util.AppContextUtil;
import org.slf4j.Logger;
import org.springframework.context.MessageSource;

import java.util.Locale;

public class R {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(R.class);

    private static Locale getDefaultLocale() {
        return Locale.SIMPLIFIED_CHINESE;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return ok(data, CodeMessageEnum.OK, null);
    }

    public static <T> Result<T> ok(T data, CodeMessage codeMessage, Object... args) {
        return okByMessage(AppContextUtil.getAppContext(), data, codeMessage, args);
    }

    public static <T> Result<T> okByMessage(MessageSource messageSource, T data, CodeMessage codeMessage, Object... args) {
        String message = messageSource.getMessage(codeMessage.getMessageKey(), args, getDefaultLocale());
        return Results.success(codeMessage.getCode(), message, data);
    }

    public static <T> Result<T> fail(T data, CodeMessage codeMessage, Object... args) {
        return failByMessage(AppContextUtil.getAppContext(),data,codeMessage,args);
    }

    public static <T> Result<T> failByMessage(MessageSource messageSource, T data, CodeMessage codeMessage, Object... args) {
        String message = messageSource.getMessage(codeMessage.getMessageKey(), args, getDefaultLocale());
        return Results.fail(codeMessage.getCode(), message, data);
    }

    public static <T> Result<T> failByMessage(MessageSource messageSource,Integer code,String messageKey, Object... args) {
        String message = messageSource.getMessage(messageKey, args, getDefaultLocale());
        return Results.fail(code, message, null);
    }

    public static <T> Result<T> fail(CodeMessage codeMessage, Object... args) {
        return fail(null, codeMessage, args);
    }
}
