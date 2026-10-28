package com.abluepoint.summer.sample.identity.application.directory;

import com.abluepoint.summer.sample.identity.domain.AuthAccount;
import com.abluepoint.summer.sample.identity.domain.AuthAccountStore;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Identity existence boundary for explicit member provisioning; never exposes password data. */
@Service
public class LocalIdentityDirectory {
    private final AuthAccountStore users;
    public LocalIdentityDirectory(AuthAccountStore users) { this.users = users; }
    @Transactional(readOnly = true)
    public boolean isActive(String subject) {
        return users.find(subject).map(AuthAccount::active).orElse(false);
    }
}
