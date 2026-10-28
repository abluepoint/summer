package com.abluepoint.summer.sample.task.infrastructure;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_task_progress_event")
public class TaskProgressEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64)
    private String enterpriseId;
    @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;
    @Column(name = "execution_id", nullable = false, updatable = false, length = 36)
    private String executionId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "text")
    private String payloadJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskProgressEventEntity() {}

    public TaskProgressEventEntity(String enterpriseId, String taskId, String executionId,
                            String eventType, String payloadJson) {
        this.enterpriseId = enterpriseId;
        this.taskId = taskId;
        this.executionId = executionId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
    }

    public Long eventId() { return eventId; }
    public String enterpriseId() { return enterpriseId; }
    public String taskId() { return taskId; }
    public String executionId() { return executionId; }
    public String eventType() { return eventType; }
    public String payloadJson() { return payloadJson; }
    public Instant createdAt() { return createdAt; }
}
