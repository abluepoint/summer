package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface AuthorizationRoleRepository extends JpaRepository<AuthorizationRoleEntity, String> {
    List<AuthorizationRoleEntity> findAllByOrderByEnterpriseIdAscCodeAsc();
}
