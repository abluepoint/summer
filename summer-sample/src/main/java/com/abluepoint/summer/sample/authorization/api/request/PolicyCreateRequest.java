package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.PolicyCreateCommand;

import com.abluepoint.summer.sample.authorization.domain.PolicyDefinition;

public record PolicyCreateRequest(
        String name,
        String code,
        String description,
        String policyType,
        PolicyDefinition definition) {
    public com.abluepoint.summer.sample.authorization.application.command.PolicyCreateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.PolicyCreateCommand(name, code, description, policyType, definition);
    }
}
