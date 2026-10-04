package com.abluepoint.summer.sample.task;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "summer_task_definition")
class TaskDefinitionEntity {
    @Id @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64)
    private String enterpriseId;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "task_type", nullable = false, length = 120) private String taskType;
    @Column(name = "cron_expression", nullable = false, length = 120) private String cronExpression;
    @Column(name = "parameters_json", columnDefinition = "text") private String parametersJson;
    @Column(name = "max_retries", nullable = false) private int maxRetries;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TaskStatus status;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TaskDefinitionEntity() {}
    TaskDefinitionEntity(String taskId, String enterpriseId, String name, String taskType,
                         String cronExpression, String parametersJson, int maxRetries) {
        this.taskId = taskId; this.enterpriseId = enterpriseId; this.name = name;
        this.taskType = taskType; this.cronExpression = cronExpression;
        this.parametersJson = parametersJson; this.maxRetries = maxRetries;
        this.status = TaskStatus.ACTIVE; this.version = 0; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    void update(String name, String cronExpression, String parametersJson, int maxRetries) {
        this.name = name; this.cronExpression = cronExpression; this.parametersJson = parametersJson;
        this.maxRetries = maxRetries; this.updatedAt = Instant.now();
    }
    void status(TaskStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    String taskId() { return taskId; } String enterpriseId() { return enterpriseId; } String name() { return name; }
    String taskType() { return taskType; } String cronExpression() { return cronExpression; } String parametersJson() { return parametersJson; }
    int maxRetries() { return maxRetries; } TaskStatus status() { return status; } long version() { return version; }
    Instant createdAt() { return createdAt; } Instant updatedAt() { return updatedAt; }
}
