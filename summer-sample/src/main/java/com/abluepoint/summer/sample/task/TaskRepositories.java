package com.abluepoint.summer.sample.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface TaskDefinitionRepository extends JpaRepository<TaskDefinitionEntity, String> {
    List<TaskDefinitionEntity> findAllByStatus(TaskStatus status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select task from TaskDefinitionEntity task where task.taskId = :taskId")
    Optional<TaskDefinitionEntity> findForExecution(@org.springframework.data.repository.query.Param("taskId") String taskId);
    List<TaskDefinitionEntity> findAllByEnterpriseIdOrderByUpdatedAtDesc(String enterpriseId);
    Optional<TaskDefinitionEntity> findByEnterpriseIdAndName(String enterpriseId, String name);
}
interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
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
interface TaskAttemptRepository extends JpaRepository<TaskAttemptEntity, String> {
    List<TaskAttemptEntity> findAllByExecutionIdOrderByAttemptAsc(String executionId);
}
interface TaskTriggerReceiptRepository extends JpaRepository<TaskTriggerReceiptEntity, String> {
    @org.springframework.data.jpa.repository.Query("select receipt from TaskTriggerReceiptEntity receipt, TaskDefinitionEntity task " +
            "where receipt.taskId = task.taskId and task.status = :status and not exists " +
            "(select execution.executionId from TaskExecutionEntity execution where execution.taskId = receipt.taskId " +
            "and execution.idempotencyKey = receipt.idempotencyKey) order by receipt.createdAt")
    List<TaskTriggerReceiptEntity> findPending(@org.springframework.data.repository.query.Param("status") TaskStatus status,
                                             org.springframework.data.domain.Pageable page);
    java.util.Optional<TaskTriggerReceiptEntity> findByEnterpriseIdAndIdempotencyKey(String enterpriseId, String idempotencyKey);
}
