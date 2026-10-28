package com.abluepoint.summer.sample.observability.controller;

import com.abluepoint.summer.sample.observability.service.TraceCorrelation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs inside Boot's server observation, before Security can commit an error. */
public final class TraceResponseFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceCorrelation.currentTraceId();
        if (traceId != null) response.setHeader("X-Trace-Id", traceId);
        chain.doFilter(request, response);
    }
}
