package com.abluepoint.summer.mvc.filter;

import com.abluepoint.summer.mvc.logging.RequestLogRecord;
import com.abluepoint.summer.mvc.logging.RequestLogSink;
import com.abluepoint.summer.mvc.request.ServletRequestIdAccessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Collects HTTP lifecycle metadata and passes an immutable snapshot to the
 * configured output sink.
 */
public final class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final RequestLogSink sink;

    public RequestLoggingFilter(RequestLogSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestLogRecord record = new RequestLogRecord(
                    ServletRequestIdAccessor.requestId(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis(startedAt)
            );
            try {
                sink.record(record);
            } catch (RuntimeException exception) {
                // Logging must not change the response or mask an application failure.
                LOGGER.warn("Unable to write HTTP request log snapshot", exception);
            }
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
