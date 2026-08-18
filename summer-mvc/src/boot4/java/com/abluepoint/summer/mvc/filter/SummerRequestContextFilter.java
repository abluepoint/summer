package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.common.context.SummerContext;
import com.abluepoint.summer.common.context.SummerContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public final class SummerRequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private final boolean cacheRequestBody;

    public SummerRequestContextFilter(boolean cacheRequestBody) {
        this.cacheRequestBody = cacheRequestBody;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader(REQUEST_ID_HEADER));
        SummerContext context = new SummerContext(requestId, LocaleContextHolder.getLocale(), Map.of());
        HttpServletRequest effectiveRequest = cacheRequestBody && hasBody(request)
                ? new CachedBodyHttpServletRequest(request)
                : request;
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        try (SummerContextHolder.Scope ignored = SummerContextHolder.open(context)) {
            filterChain.doFilter(effectiveRequest, response);
        } finally {
            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID_MDC_KEY);
            } else {
                MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
            }
        }
    }

    private static boolean hasBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        boolean containerManagedForm = contentType != null
                && (contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")
                || contentType.toLowerCase().startsWith("multipart/"));
        return !containerManagedForm
                && request.getContentLengthLong() != 0
                && !"GET".equalsIgnoreCase(request.getMethod())
                && !"HEAD".equalsIgnoreCase(request.getMethod());
    }

    private static String normalizeRequestId(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 128) {
            return UUID.randomUUID().toString();
        }
        return candidate.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
