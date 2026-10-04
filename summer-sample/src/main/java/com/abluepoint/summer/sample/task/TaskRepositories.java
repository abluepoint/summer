package com.abluepoint.summer.sample.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface TaskDefinitionRepository extends JpaRepository<TaskDefinitionEntity, String> {
    List<TaskDefinitionEntity> findAllByEnterpriseIdOrderByUpdatedAtDesc(String enterpriseId);
    Optional<TaskDefinitionEntity> findByEnterpriseIdAndName(String enterpriseId, String name);
}
interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
    List<TaskExecutionEntity> findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc(String enterpriseId, String taskId);
    java.util.Optional<TaskExecutionEntity> findByTaskIdAndFireInstanceId(String taskId, String fireInstanceId);
    java.util.Optional<TaskExecutionEntity> findByTaskIdAndIdempotencyKey(String taskId, String idempotencyKey);
}
interface TaskAttemptRepository extends JpaRepository<TaskAttemptEntity, String> {
    List<TaskAttemptEntity> findAllByExecutionIdOrderByAttemptAsc(String executionId);
}
interface TaskTriggerReceiptRepository extends JpaRepository<TaskTriggerReceiptEntity, String> {
    java.util.Optional<TaskTriggerReceiptEntity> findByEnterpriseIdAndIdempotencyKey(String enterpriseId, String idempotencyKey);
}
