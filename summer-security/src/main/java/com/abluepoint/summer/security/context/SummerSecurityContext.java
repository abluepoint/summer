package com.abluepoint.summer.security.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

public final class SummerSecurityContext {

    private SummerSecurityContext() {
    }

    public static String principalName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? "anonymous"
                : authentication.getName();
    }

    public static Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns whether the current Spring Security principal is authenticated.
     * The framework deliberately reads the native SecurityContext instead of
     * introducing a product session or a second request-scoped context.
     */
    public static boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    public static boolean hasAuthority(String authority) {
        return authenticated() && authorities().contains(authority);
    }

    public static boolean hasScope(String scope) {
        return scope != null && hasAuthority("SCOPE_" + scope);
    }
}
