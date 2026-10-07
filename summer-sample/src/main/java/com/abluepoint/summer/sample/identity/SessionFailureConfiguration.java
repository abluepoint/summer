package com.abluepoint.summer.sample.identity;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.common.response.ApiError;
import tools.jackson.databind.ObjectMapper;

/** Covers failures from Spring Session, which runs before Spring Security and MVC. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.storage", name = "session", havingValue = "redis")
class SessionFailureConfiguration {
    @Bean FilterRegistrationBean<OncePerRequestFilter> sharedSessionFailureFilter(ObjectMapper mapper) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, IOException {
                try { chain.doFilter(request, response); }
                catch (RuntimeException | jakarta.servlet.ServletException failure) {
                    Throwable cause = failure;
                    boolean unavailable = false;
                    for (int i = 0; cause != null && i < 20; i++, cause = cause.getCause()) {
                        if (cause instanceof SessionIndexUnavailableException
                                || cause instanceof org.springframework.data.redis.RedisConnectionFailureException
                                || cause instanceof org.springframework.data.redis.RedisSystemException) unavailable = true;
                    }
                    if (!unavailable || response.isCommitted()) throw failure;
                    response.reset();
                    response.setStatus(503);
                    response.setContentType("application/json");
                    response.setHeader("Cache-Control", "no-store");
                    String requestId = java.util.UUID.randomUUID().toString();
                    response.setHeader("X-Request-Id", requestId);
                    mapper.writeValue(response.getOutputStream(), ApiResponse.failure(new ApiError(
                            "SUMMER_SESSION_STORE_UNAVAILABLE", "Shared session storage is unavailable", List.of()), requestId));
                }
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
