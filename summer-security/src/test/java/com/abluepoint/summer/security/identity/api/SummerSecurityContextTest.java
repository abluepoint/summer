package com.abluepoint.summer.security.identity.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SummerSecurityContextTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void treatsUnauthenticatedTokenAsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("pending-user", "secret")
        );

        assertThat(SummerSecurityContext.principalName()).isEqualTo("anonymous");
        assertThat(SummerSecurityContext.authorities()).isEmpty();
        assertThat(SummerSecurityContext.authenticated()).isFalse();
        assertThat(SummerSecurityContext.hasScope("customer.read")).isFalse();
    }

    @Test
    void exposesNativeAuthoritiesAndScopesWithoutAProductContext() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "principal-1",
                        "n/a",
                        java.util.List.of(
                                new SimpleGrantedAuthority("SCOPE_customer.read"),
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                )
        );

        assertThat(SummerSecurityContext.authenticated()).isTrue();
        assertThat(SummerSecurityContext.principalName()).isEqualTo("principal-1");
        assertThat(SummerSecurityContext.hasScope("customer.read")).isTrue();
        assertThat(SummerSecurityContext.hasScope("customer.write")).isFalse();
        assertThat(SummerSecurityContext.hasAuthority("ROLE_USER")).isTrue();
    }
}
