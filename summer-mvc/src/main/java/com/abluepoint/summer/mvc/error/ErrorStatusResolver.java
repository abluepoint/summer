package com.abluepoint.summer.mvc.error;

import com.abluepoint.summer.common.error.api.ErrorCode;
import org.springframework.http.HttpStatus;

@FunctionalInterface
public interface ErrorStatusResolver {

    HttpStatus resolve(ErrorCode errorCode);
}
