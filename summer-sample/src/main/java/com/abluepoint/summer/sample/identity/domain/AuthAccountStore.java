package com.abluepoint.summer.sample.identity.domain;

import java.util.List;
import java.util.Optional;

public interface AuthAccountStore {
    Optional<AuthAccount> find(String username);
    List<AuthAccount> list();
    boolean exists(String username);
    AuthAccount create(String username, String passwordHash, String role);
    AuthAccount setActive(String username, boolean active);
}
