package com.abluepoint.summer.sample.statistics;

import java.time.Instant;

record StatisticsWindow(Instant from, Instant to) {}

record StatisticsScope(String enterpriseId, String organizationId, String subject) {}

record StatisticsView(
        StatisticsWindow window,
        StatisticsScope scope,
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
