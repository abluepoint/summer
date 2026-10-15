package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface AuthorizationUserBindingRepository extends JpaRepository<AuthorizationUserBindingEntity, String> {
    Optional<AuthorizationUserBindingEntity> findByEnterpriseIdAndUsername(String enterpriseId, String username);
    List<AuthorizationUserBindingEntity> findAllByOrderByEnterpriseIdAscUsernameAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthorizationUserBindingEntity b set b.roleIds = :roles, b.deniedCapabilityCodes = :denied, b.version = b.version + 1, b.updatedAt = :now where b.enterpriseId = :enterprise and b.username = :username and b.version = :expected")
    int updateVersion(@Param("enterprise") String enterprise, @Param("username") String username,
                      @Param("roles") String roles, @Param("denied") String denied,
                      @Param("expected") long expected, @Param("now") java.time.Instant now);
}
