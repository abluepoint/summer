package com.abluepoint.summer.sample.key;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

interface KeyRotationStateRepository extends JpaRepository<KeyRotationStateEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from KeyRotationStateEntity state where state.id = 1")
    Optional<KeyRotationStateEntity> lockForRotation();
}
