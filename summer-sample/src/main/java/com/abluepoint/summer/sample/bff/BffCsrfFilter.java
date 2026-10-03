package com.abluepoint.summer.sample.bff;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

final class BffCsrfFilter extends OncePerRequestFilter {

    static final String CSRF_HEADER = "X-CSRF-Token";

    private final List<String> allowedOrigins;

    BffCsrfFilter(List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isStateChanging(request) && request.getRequestURI().startsWith("/bff/")) {
            String origin = request.getHeader("Origin");
            String header = request.getHeader(CSRF_HEADER);
            String cookie = csrfCookie(request);
            if (origin == null || !allowedOrigins.contains(origin) || !matches(header, cookie)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF validation failed");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean matches(String header, String cookie) {
        if (header == null || cookie == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                header.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                cookie.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private static boolean isStateChanging(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private static String csrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (BffCookiePolicy.CSRF_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
