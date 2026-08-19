package com.abluepoint.summer.security.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    }
}
