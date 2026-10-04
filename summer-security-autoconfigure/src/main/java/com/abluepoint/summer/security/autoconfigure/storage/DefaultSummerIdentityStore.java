package com.abluepoint.summer.security.autoconfigure.storage;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Objects;

/** Delegating identity store used by the default in-memory adapter. */
public final class DefaultSummerIdentityStore implements SummerIdentityStore {

    private final UserDetailsService delegate;

    public DefaultSummerIdentityStore(UserDetailsService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return delegate.loadUserByUsername(username);
    }
}
