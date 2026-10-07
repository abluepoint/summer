package com.abluepoint.summer.security.autoconfigure.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.*;

/** Bearer credentials are request scoped, even when the browser also sends a login cookie. */
public final class BearerOrSessionSecurityContextRepository implements SecurityContextRepository {
    private final SecurityContextRepository sessions;
    private final SecurityContextRepository bearer = new RequestAttributeSecurityContextRepository();

    public BearerOrSessionSecurityContextRepository(SecurityContextRepository sessions) { this.sessions = sessions; }

    public static boolean hasBearerCredentials(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.regionMatches(true, 0, "Bearer", 0, 6);
    }

    private SecurityContextRepository repository(HttpServletRequest request) {
        return hasBearerCredentials(request) ? bearer : sessions;
    }

    @Override public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        return repository(holder.getRequest()).loadContext(holder);
    }
    @Override public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        return repository(request).loadDeferredContext(request);
    }
    @Override public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        repository(request).saveContext(context, request, response);
    }
    @Override public boolean containsContext(HttpServletRequest request) {
        return repository(request).containsContext(request);
    }
}
