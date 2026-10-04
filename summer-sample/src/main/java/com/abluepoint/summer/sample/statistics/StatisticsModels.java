package com.abluepoint.summer.sample.statistics;

import java.time.Instant;

record StatisticsWindow(Instant from, Instant to) {}

record StatisticsScope(String enterpriseId, String organizationId, String subject) {}

record StatisticsView(
        StatisticsWindow window,
        StatisticsScope scope,
        long userCount,
        long roleCount,
        long interfaceCount,
        long accessibleInterfaceCount,
        long dictionaryCount,
        long taskCount,
        long taskExecutionCount,
        long successfulTaskExecutionCount,
        long failedTaskExecutionCount,
        double taskSuccessRate,
        long auditEventCount
) {}
