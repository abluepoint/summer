package com.abluepoint.summer.mvc.filter;

import java.io.IOException;

/** Signals that the configured request body reception limit was exceeded. */
final class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException() {
        super("Request body exceeds the configured limit");
    }
}
