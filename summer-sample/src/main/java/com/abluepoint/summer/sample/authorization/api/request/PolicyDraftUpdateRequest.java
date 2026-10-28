package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.PolicyDraftUpdateCommand;

import com.abluepoint.summer.sample.authorization.domain.PolicyDefinition;

public record PolicyDraftUpdateRequest(
        String name,
        String code,
        String description,
        String policyType,
        PolicyDefinition definition) {
    public com.abluepoint.summer.sample.authorization.application.command.PolicyDraftUpdateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.PolicyDraftUpdateCommand(name, code, description, policyType, definition);
    }
}
