package com.abluepoint.summer.mvc.error.internal;

import com.abluepoint.summer.mvc.error.spi.ErrorStatusResolver;

import com.abluepoint.summer.common.error.api.ErrorCode;
import org.springframework.http.HttpStatus;

public final class DefaultErrorStatusResolver implements ErrorStatusResolver {

    @Override
    public HttpStatus resolve(ErrorCode errorCode) {
        return switch (errorCode.category()) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
