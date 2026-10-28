package com.abluepoint.summer.sample.authorization.application.view;

import java.time.Instant;
import java.util.Map;

public record PolicyTestCaseView(
        String testCaseId,
        String policyId,
        String name,
        Map<String, Object> input,
        String expectedDecision,
        String lastActualDecision,
        Instant lastRunAt) {}
