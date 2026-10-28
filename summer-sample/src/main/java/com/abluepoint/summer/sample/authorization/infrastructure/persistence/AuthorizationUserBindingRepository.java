package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AuthorizationUserBindingRepository extends JpaRepository<AuthorizationUserBindingEntity, String> {
    public Optional<AuthorizationUserBindingEntity> findByEnterpriseIdAndUsername(String enterpriseId, String username);
    public List<AuthorizationUserBindingEntity> findAllByOrderByEnterpriseIdAscUsernameAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthorizationUserBindingEntity b set b.roleIds = :roles, b.deniedCapabilityCodes = :denied, b.version = b.version + 1, b.updatedAt = :now where b.enterpriseId = :enterprise and b.username = :username and b.version = :expected")
    public int updateVersion(@Param("enterprise") String enterprise, @Param("username") String username,
                      @Param("roles") String roles, @Param("denied") String denied,
                      @Param("expected") long expected, @Param("now") java.time.Instant now);
}
