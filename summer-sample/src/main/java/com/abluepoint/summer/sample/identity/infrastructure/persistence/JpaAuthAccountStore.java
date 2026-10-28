package com.abluepoint.summer.sample.identity.infrastructure.persistence;

import com.abluepoint.summer.sample.identity.domain.AuthAccount;
import com.abluepoint.summer.sample.identity.domain.AuthAccountStore;
import com.abluepoint.summer.sample.identity.domain.AuthUserConflictException;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaAuthAccountStore implements AuthAccountStore {
    private final AuthUserRepository users;
    public JpaAuthAccountStore(AuthUserRepository users) { this.users = users; }
    @Override public Optional<AuthAccount> find(String username) { return users.findByUsername(username).map(JpaAuthAccountStore::account); }
    @Override public List<AuthAccount> list() { return users.findAllByOrderByUsernameAsc().stream().map(JpaAuthAccountStore::account).toList(); }
    @Override public boolean exists(String username) { return users.existsByUsername(username); }
    @Override public AuthAccount create(String username, String hash, String role) {
        try {
            var user = users.save(new AuthUserEntity(username, hash, role));
            users.flush();
            return account(user);
        } catch (DataIntegrityViolationException conflict) { throw new AuthUserConflictException(); }
    }
    @Override public AuthAccount setActive(String username, boolean active) {
        var user = users.findByUsername(username).orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
        user.active(active);
        return account(user);
    }
    private static AuthAccount account(AuthUserEntity user) {
        return new AuthAccount(user.username(), user.passwordHash(), user.role(), user.active());
    }
}
