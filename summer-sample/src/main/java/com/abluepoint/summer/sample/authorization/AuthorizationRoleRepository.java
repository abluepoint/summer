package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

interface AuthorizationRoleRepository extends JpaRepository<AuthorizationRoleEntity, String> {
    List<AuthorizationRoleEntity> findAllByOrderByEnterpriseIdAscCodeAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthorizationRoleEntity r set r.version = r.version + 1 where r.roleId = :id and r.enterpriseId = :enterprise and r.version = :expected")
    int claimVersion(@Param("id") String id, @Param("enterprise") String enterprise, @Param("expected") long expected);
}
