package com.abluepoint.summer.sample.identity.application;

import com.abluepoint.summer.sample.identity.domain.AuthAccount;
import com.abluepoint.summer.sample.identity.domain.AuthAccountStore;
import com.abluepoint.summer.sample.identity.domain.AuthUserConflictException;


import com.abluepoint.summer.security.identity.spi.SummerIdentityStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abluepoint.summer.sample.identity.application.command.AuthUserCreateCommand;
import com.abluepoint.summer.sample.identity.application.command.AuthUserActivationCommand;
import java.util.List;

@Service
public class AuthUserService implements SummerIdentityStore {
    private final AuthAccountStore repository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AuthUserService(AuthAccountStore repository, PasswordEncoder passwordEncoder,
                    @Value("${summer.security.authorization-server.user.username:}") String bootstrapUsername,
                    @Value("${summer.security.authorization-server.user.password:}") String bootstrapPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void bootstrapFirstAdmin() {
        if (bootstrapUsername == null || bootstrapUsername.isBlank() || bootstrapPassword == null || bootstrapPassword.isBlank()) return;
        if (!repository.exists(bootstrapUsername)) {
            repository.create(bootstrapUsername, passwordEncoder.encode(bootstrapPassword), "ADMIN");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthAccount user = repository.find(username)
                .filter(AuthAccount::availableForAuthentication)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.username()).password(user.passwordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.role())).build();
    }

    @Transactional(readOnly = true)
    public List<AuthUserView> list() { return repository.list().stream().map(AuthUserView::from).toList(); }

    @Transactional
    public AuthUserView create(AuthUserCreateCommand command) {
        String username = command.username();
        String password = command.password();
        String role = command.role();
        if (repository.exists(username)) throw new AuthUserConflictException();
        return AuthUserView.from(repository.create(username, passwordEncoder.encode(password), role));
    }

    @Transactional
    public AuthUserView setActive(AuthUserActivationCommand command) {
        String username = command.username();
        boolean active = command.active();
        return AuthUserView.from(repository.setActive(username, active));
    }


}
