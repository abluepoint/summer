package com.abluepoint.summer.security.access.internal;

import jakarta.servlet.http.HttpServletRequest;

final class SecurityRequestIds {

    private static final String REQUEST_ID_ATTRIBUTE = "com.abluepoint.summer.requestId";

    private SecurityRequestIds() {
    }

    static String from(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank()
                ? value
                : request.getRequestId();
    }
}
