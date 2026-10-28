package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.domain.TaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskDefinitionRepository extends JpaRepository<TaskDefinitionEntity, String> {
    List<TaskDefinitionEntity> findAllByStatus(TaskStatus status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select task from TaskDefinitionEntity task where task.taskId = :taskId")
    Optional<TaskDefinitionEntity> findForExecution(@org.springframework.data.repository.query.Param("taskId") String taskId);
    List<TaskDefinitionEntity> findAllByEnterpriseIdOrderByUpdatedAtDesc(String enterpriseId);
    Optional<TaskDefinitionEntity> findByEnterpriseIdAndName(String enterpriseId, String name);
}
