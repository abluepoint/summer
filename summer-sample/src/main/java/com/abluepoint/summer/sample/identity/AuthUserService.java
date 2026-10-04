package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.security.autoconfigure.storage.SummerIdentityStore;
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

import java.util.List;

@Service
class AuthUserService implements SummerIdentityStore {
    private final AuthUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    AuthUserService(AuthUserRepository repository, PasswordEncoder passwordEncoder,
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
        if (!repository.existsByUsername(bootstrapUsername)) {
            repository.save(new AuthUser(bootstrapUsername, passwordEncoder.encode(bootstrapPassword), "ADMIN"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUser user = repository.findByUsername(username)
                .filter(AuthUser::active)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.username()).password(user.passwordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.role())).build();
    }

    @Transactional(readOnly = true)
    List<AuthUserView> list() { return repository.findAllByOrderByUsernameAsc().stream().map(AuthUserView::from).toList(); }

    @Transactional
    AuthUserView create(String username, String password, String role) {
        if (repository.existsByUsername(username)) throw new IllegalArgumentException("Username already exists");
        AuthUser user = repository.save(new AuthUser(username, passwordEncoder.encode(password), role));
        return AuthUserView.from(user);
    }

    @Transactional
    AuthUserView setActive(String username, boolean active) {
        AuthUser user = repository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        user.active(active);
        return AuthUserView.from(user);
    }

    record AuthUserView(String username, String role, boolean active) {
        static AuthUserView from(AuthUser user) { return new AuthUserView(user.username(), user.role(), user.active()); }
    }
}
