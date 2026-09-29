package com.abluepoint.summer.mvc.request;

import com.abluepoint.summer.mvc.filter.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ServletRequestIdAccessor implements RequestIdAccessor {

    public static final String UNBOUND_REQUEST_ID = "unbound";

    @Override
    public String currentRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return requestId(servletAttributes.getRequest());
        }
        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? UNBOUND_REQUEST_ID : requestId;
    }

    public static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank()
                ? value
                : fallbackRequestId(request);
    }

    private static String fallbackRequestId(HttpServletRequest request) {
        String requestId = request.getRequestId();
        return requestId == null || requestId.isBlank() ? UNBOUND_REQUEST_ID : requestId;
    }
}
