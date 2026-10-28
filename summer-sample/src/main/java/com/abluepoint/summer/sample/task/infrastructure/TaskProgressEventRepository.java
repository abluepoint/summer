package com.abluepoint.summer.sample.task.infrastructure;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskProgressEventRepository extends JpaRepository<TaskProgressEventEntity, Long> {
    List<TaskProgressEventEntity> findTop501ByEnterpriseIdAndTaskIdOrderByEventIdDesc(String enterpriseId, String taskId);
    List<TaskProgressEventEntity> findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc(String enterpriseId, String taskId);
    List<TaskProgressEventEntity> findTop500ByEnterpriseIdAndTaskIdAndEventIdGreaterThanOrderByEventIdAsc(
            String enterpriseId, String taskId, Long eventId);
}
