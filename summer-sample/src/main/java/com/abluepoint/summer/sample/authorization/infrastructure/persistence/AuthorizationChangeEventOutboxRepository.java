package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import com.abluepoint.summer.sample.authorization.domain.AuthorizationChangeEventStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface AuthorizationChangeEventOutboxRepository extends JpaRepository<AuthorizationChangeEventOutboxEntity, String> {
    public List<AuthorizationChangeEventOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(AuthorizationChangeEventStatus status, Instant now);
    public List<AuthorizationChangeEventOutboxEntity> findAllByEnterpriseIdOrderByCreatedAtDesc(String enterpriseId);
    public List<AuthorizationChangeEventOutboxEntity> findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(String enterpriseId, String resourceType);
}
