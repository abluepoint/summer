package com.abluepoint.summer.sample.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthUserServiceTest {
    @Test
    void createsOnlyEncodedPasswordsAndSupportsDeactivateRestore() {
        AuthUserRepository repository = mock(AuthUserRepository.class);
        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AuthUserService service = new AuthUserService(repository, encoder, "", "");

        AuthUserService.AuthUserView created = service.create("alice", "a-strong-password", "USER");
        verify(repository).save(argThat(user -> !user.passwordHash().equals("a-strong-password")
                && encoder.matches("a-strong-password", user.passwordHash())));
        assertThat(created.active()).isTrue();
    }
}
