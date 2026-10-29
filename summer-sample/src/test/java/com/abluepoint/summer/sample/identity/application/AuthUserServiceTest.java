package com.abluepoint.summer.sample.identity.application;

import com.abluepoint.summer.sample.identity.infrastructure.persistence.AuthUserEntity;
import com.abluepoint.summer.sample.identity.infrastructure.persistence.AuthUserRepository;

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
        when(repository.save(any(AuthUserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AuthUserService service = new AuthUserService(new com.abluepoint.summer.sample.identity.infrastructure.persistence.JpaAuthAccountStore(repository), encoder, "", "");

        AuthUserView created = service.create(new com.abluepoint.summer.sample.identity.api.AuthUserCreateRequest("alice", "a-strong-password", "USER"));
        verify(repository).save(argThat(user -> !user.passwordHash().equals("a-strong-password")
                && encoder.matches("a-strong-password", user.passwordHash())));
        assertThat(created.active()).isTrue();
    }
}
