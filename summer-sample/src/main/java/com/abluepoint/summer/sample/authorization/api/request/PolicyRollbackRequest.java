package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.PolicyRollbackCommand;

public record PolicyRollbackRequest(Integer versionNo) {
    public com.abluepoint.summer.sample.authorization.application.command.PolicyRollbackCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.PolicyRollbackCommand(versionNo);
    }
}
