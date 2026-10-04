package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AuthorizationUserBindingRepository extends JpaRepository<AuthorizationUserBindingEntity, String> {
    Optional<AuthorizationUserBindingEntity> findByEnterpriseIdAndUsername(String enterpriseId, String username);
    List<AuthorizationUserBindingEntity> findAllByOrderByEnterpriseIdAscUsernameAsc();
}
