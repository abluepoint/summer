package com.abluepoint.summer.sample.task.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record TaskExecutionPageView(List<TaskExecutionView> items, long totalElements) {}
