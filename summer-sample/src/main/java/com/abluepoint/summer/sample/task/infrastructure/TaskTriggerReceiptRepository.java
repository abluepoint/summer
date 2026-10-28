package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TaskTriggerReceiptRepository extends JpaRepository<TaskTriggerReceiptEntity, String> {
    @org.springframework.data.jpa.repository.Query("select receipt from TaskTriggerReceiptEntity receipt, TaskDefinitionEntity task " +
            "where receipt.taskId = task.taskId and task.status = :status and not exists " +
            "(select execution.executionId from TaskExecutionEntity execution where execution.taskId = receipt.taskId " +
            "and execution.idempotencyKey = receipt.idempotencyKey) order by receipt.createdAt")
    List<TaskTriggerReceiptEntity> findPending(@org.springframework.data.repository.query.Param("status") TaskStatus status,
                                             org.springframework.data.domain.Pageable page);
    java.util.Optional<TaskTriggerReceiptEntity> findByEnterpriseIdAndIdempotencyKey(String enterpriseId, String idempotencyKey);
}
