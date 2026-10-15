package com.abluepoint.summer.sample.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Identity existence boundary for explicit member provisioning; never exposes password data. */
@Service
public class LocalIdentityDirectory {
    private final AuthUserRepository users;
    LocalIdentityDirectory(AuthUserRepository users) { this.users = users; }
    @Transactional(readOnly = true)
    public boolean isActive(String subject) {
        return users.findByUsername(subject).map(AuthUser::active).orElse(false);
    }
}
