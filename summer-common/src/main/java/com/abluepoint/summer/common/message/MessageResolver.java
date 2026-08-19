package com.abluepoint.summer.common.message;

import com.abluepoint.summer.common.error.ErrorCode;

import java.util.Locale;

@FunctionalInterface
public interface MessageResolver {

    String resolve(ErrorCode errorCode, Object[] arguments, Locale locale);
}
