package com.abluepoint.summer.sample.task.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record TaskExecutionView(String executionId, String taskId, String enterpriseId,
                         String fireInstanceId, String status, int attempt,
                         Instant startedAt, Instant finishedAt, String failureCode,
                         String idempotencyKey, boolean recovered, List<TaskAttemptView> attempts) {}
