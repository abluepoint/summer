package com.abluepoint.summer.sample.statistics.model.view;

import java.time.Instant;

public record StatisticsView(
        StatisticsWindowView window,
        StatisticsScopeView scope,
        Long userCount,
        Long roleCount,
        Long interfaceCount,
        Long accessibleInterfaceCount,
        Long dictionaryCount,
        Long taskCount,
        Long taskExecutionCount,
        Long successfulTaskExecutionCount,
        Long failedTaskExecutionCount,
        Double taskSuccessRate,
        Long auditEventCount,
        Instant asOf
) {}
