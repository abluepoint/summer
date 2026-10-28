package com.abluepoint.summer.sample.identity.infrastructure.persistence;

import com.abluepoint.summer.data.jpa.persistence.api.BaseRepository;
import java.util.List;
import java.util.Optional;

public interface AuthUserRepository extends BaseRepository<AuthUserEntity, Long> {
    Optional<AuthUserEntity> findByUsername(String username);
    List<AuthUserEntity> findAllByOrderByUsernameAsc();
    boolean existsByUsername(String username);
}
