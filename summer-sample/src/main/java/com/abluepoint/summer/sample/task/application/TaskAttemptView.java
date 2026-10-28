package com.abluepoint.summer.sample.task.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record TaskAttemptView(int attempt, String status, Instant startedAt, Instant finishedAt,
                       String failureCode, String nodeId) {}
