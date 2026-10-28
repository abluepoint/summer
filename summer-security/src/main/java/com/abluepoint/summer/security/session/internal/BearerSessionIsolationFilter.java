package com.abluepoint.summer.security.session.internal;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Prevent incidental MVC/session diagnostics from coupling bearer requests to login storage. */
public final class BearerSessionIsolationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!com.abluepoint.summer.security.identity.api.SummerSecurityContext.hasBearerCredentials(request)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public HttpSession getSession(boolean create) {
                if (create) throw new IllegalStateException("Bearer requests do not establish login sessions");
                return null;
            }
            @Override public HttpSession getSession() { return getSession(true); }
            @Override public String getRequestedSessionId() { return null; }
            @Override public boolean isRequestedSessionIdValid() { return false; }
            @Override public boolean isRequestedSessionIdFromCookie() { return false; }
            @Override public boolean isRequestedSessionIdFromURL() { return false; }
            @Override public String changeSessionId() { throw new IllegalStateException("No login session on a bearer request"); }
        }, response);
    }
}
