package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.data.jpa.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

interface AuthUserRepository extends BaseRepository<AuthUser, Long> {
    Optional<AuthUser> findByUsername(String username);
    List<AuthUser> findAllByOrderByUsernameAsc();
    boolean existsByUsername(String username);
}
