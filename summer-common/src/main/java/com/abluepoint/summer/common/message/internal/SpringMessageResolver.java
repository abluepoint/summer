package com.abluepoint.summer.common.message.internal;

import com.abluepoint.summer.common.error.api.ErrorCode;
import com.abluepoint.summer.common.message.spi.MessageResolver;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Objects;

/** Framework-internal adapter, public for use by the Common autoconfiguration module. */
public final class SpringMessageResolver implements MessageResolver {

    private final MessageSource messageSource;

    public SpringMessageResolver(MessageSource messageSource) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    @Override
    public String resolve(ErrorCode errorCode, Object[] arguments, Locale locale) {
        return messageSource.getMessage(
                errorCode.code(),
                arguments,
                errorCode.defaultMessage(),
                locale == null ? Locale.getDefault() : locale
        );
    }
}
