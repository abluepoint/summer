package com.abluepoint.summer.sample.task.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TaskAttemptRepository extends JpaRepository<TaskAttemptEntity, String> {
    List<TaskAttemptEntity> findAllByExecutionIdOrderByAttemptAsc(String executionId);
}
