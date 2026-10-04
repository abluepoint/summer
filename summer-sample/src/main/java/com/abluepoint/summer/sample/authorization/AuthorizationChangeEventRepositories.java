package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface AuthorizationChangeEventOutboxRepository extends JpaRepository<AuthorizationChangeEventOutboxEntity, String> {
    List<AuthorizationChangeEventOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(AuthorizationChangeEventStatus status, Instant now);
    List<AuthorizationChangeEventOutboxEntity> findAllByEnterpriseIdOrderByCreatedAtDesc(String enterpriseId);
    List<AuthorizationChangeEventOutboxEntity> findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(String enterpriseId, String resourceType);
}

interface AuthorizationChangeEventReceiptRepository extends JpaRepository<AuthorizationChangeEventReceiptEntity, String> {}
