package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.domain.TaskStatus;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "summer_task_definition")
public class TaskDefinitionEntity {
    @Id @Column(name = "task_id", nullable = false, updatable = false, length = 36)
    private String taskId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64)
    private String enterpriseId;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "task_type", nullable = false, length = 120) private String taskType;
    @Column(name = "cron_expression", nullable = false, length = 120) private String cronExpression;
    @Column(name = "parameters_json", columnDefinition = "text") private String parametersJson;
    @Column(name = "max_retries", nullable = false) private int maxRetries;
    @Column(name = "timeout_ms", nullable = false) private long timeoutMs;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TaskStatus status;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TaskDefinitionEntity() {}
    public TaskDefinitionEntity(String taskId, String enterpriseId, String name, String taskType,
                         String cronExpression, String parametersJson, int maxRetries) {
        this(taskId, enterpriseId, name, taskType, cronExpression, parametersJson, maxRetries, 30_000L);
    }
    public TaskDefinitionEntity(String taskId, String enterpriseId, String name, String taskType,
                         String cronExpression, String parametersJson, int maxRetries, long timeoutMs) {
        this.taskId = taskId; this.enterpriseId = enterpriseId; this.name = name;
        this.taskType = taskType; this.cronExpression = cronExpression;
        this.parametersJson = parametersJson; this.maxRetries = maxRetries; this.timeoutMs = timeoutMs;
        this.status = TaskStatus.ACTIVE; this.version = 0; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public void update(String name, String cronExpression, String parametersJson, int maxRetries, long timeoutMs) {
        this.name = name; this.cronExpression = cronExpression; this.parametersJson = parametersJson;
        this.maxRetries = maxRetries; this.timeoutMs = timeoutMs; this.updatedAt = Instant.now();
    }
    public void status(TaskStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public String taskId() { return taskId; } public String enterpriseId() { return enterpriseId; } public String name() { return name; }
    public String taskType() { return taskType; } public String cronExpression() { return cronExpression; } public String parametersJson() { return parametersJson; }
    public int maxRetries() { return maxRetries; } public long timeoutMs() { return timeoutMs; } public TaskStatus status() { return status; } public long version() { return version; }
    public Instant createdAt() { return createdAt; } public Instant updatedAt() { return updatedAt; }
}
