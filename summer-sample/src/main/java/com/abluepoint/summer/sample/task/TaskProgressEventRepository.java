package com.abluepoint.summer.sample.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TaskProgressEventRepository extends JpaRepository<TaskProgressEventEntity, Long> {
    List<TaskProgressEventEntity> findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc(String enterpriseId, String taskId);
    List<TaskProgressEventEntity> findTop500ByEnterpriseIdAndTaskIdAndEventIdGreaterThanOrderByEventIdAsc(
            String enterpriseId, String taskId, Long eventId);
}
