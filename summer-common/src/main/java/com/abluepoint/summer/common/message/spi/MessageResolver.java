package com.abluepoint.summer.common.message.spi;

import com.abluepoint.summer.common.error.api.ErrorCode;

import java.util.Locale;

@FunctionalInterface
public interface MessageResolver {

    String resolve(ErrorCode errorCode, Object[] arguments, Locale locale);
}
