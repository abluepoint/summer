package com.abluepoint.summer.mvc.error;

import com.abluepoint.summer.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

public final class DefaultErrorStatusResolver implements ErrorStatusResolver {

    private final Map<ErrorCode, HttpStatus> statuses;

    public DefaultErrorStatusResolver() {
        this(Map.of());
    }

    /** Explicit transport policy keyed by the actual ErrorCode, with category fallback. */
    public DefaultErrorStatusResolver(Map<? extends ErrorCode, HttpStatus> statuses) {
        var mappings = new java.util.HashMap<ErrorCode, HttpStatus>();
        mappings.put(MvcErrorCode.REQUEST_BODY_TOO_LARGE, HttpStatus.valueOf(413));
        mappings.putAll(statuses);
        this.statuses = Map.copyOf(mappings);
    }

    @Override
    public HttpStatus resolve(ErrorCode errorCode) {
        HttpStatus mapped = statuses.get(errorCode);
        if (mapped != null) {
            return mapped;
        }
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
