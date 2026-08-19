package com.abluepoint.summer.mvc.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class CachedBodyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest effectiveRequest = hasBody(request)
                ? new CachedBodyHttpServletRequest(request)
                : request;
        filterChain.doFilter(effectiveRequest, response);
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
}
