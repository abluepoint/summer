package com.abluepoint.summer.sample.authorization.application.command;

import java.util.Map;

public record PolicyTestCaseCreateCommand(
        String name,
        Map<String, Object> input,
        String expectedDecision) {}
