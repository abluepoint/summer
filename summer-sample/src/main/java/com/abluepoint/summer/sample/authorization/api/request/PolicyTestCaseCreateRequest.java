package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.PolicyTestCaseCreateCommand;

import java.util.Map;

public record PolicyTestCaseCreateRequest(
        String name,
        Map<String, Object> input,
        String expectedDecision) {
    public com.abluepoint.summer.sample.authorization.application.command.PolicyTestCaseCreateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.PolicyTestCaseCreateCommand(name, input, expectedDecision);
    }
}
