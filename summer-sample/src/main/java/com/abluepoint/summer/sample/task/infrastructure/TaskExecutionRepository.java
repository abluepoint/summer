package com.abluepoint.summer.sample.task.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select execution from TaskExecutionEntity execution where execution.taskId = :taskId and execution.idempotencyKey = :key")
    Optional<TaskExecutionEntity> findClaimForUpdate(@org.springframework.data.repository.query.Param("taskId") String taskId,
                                                   @org.springframework.data.repository.query.Param("key") String key);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select execution from TaskExecutionEntity execution where execution.executionId = :executionId")
    Optional<TaskExecutionEntity> findForUpdate(@org.springframework.data.repository.query.Param("executionId") String executionId);
    List<TaskExecutionEntity> findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc(String enterpriseId, String taskId);
    java.util.Optional<TaskExecutionEntity> findByTaskIdAndFireInstanceId(String taskId, String fireInstanceId);
    java.util.Optional<TaskExecutionEntity> findByTaskIdAndIdempotencyKey(String taskId, String idempotencyKey);
}
