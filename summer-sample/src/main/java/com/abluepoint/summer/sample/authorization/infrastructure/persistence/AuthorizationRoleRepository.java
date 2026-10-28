package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AuthorizationRoleRepository extends JpaRepository<AuthorizationRoleEntity, String> {
    public List<AuthorizationRoleEntity> findAllByOrderByEnterpriseIdAscCodeAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthorizationRoleEntity r set r.version = r.version + 1 where r.roleId = :id and r.enterpriseId = :enterprise and r.version = :expected")
    public int claimVersion(@Param("id") String id, @Param("enterprise") String enterprise, @Param("expected") long expected);
}
