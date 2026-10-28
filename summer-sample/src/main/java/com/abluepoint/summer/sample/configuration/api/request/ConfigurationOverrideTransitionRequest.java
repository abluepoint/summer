package com.abluepoint.summer.sample.configuration.api.request;

import com.abluepoint.summer.sample.configuration.application.command.ConfigurationOverrideTransitionCommand;
public record ConfigurationOverrideTransitionRequest(Long revision,Long expectedVersion) {
    public com.abluepoint.summer.sample.configuration.application.command.ConfigurationOverrideTransitionCommand toCommand() { return new com.abluepoint.summer.sample.configuration.application.command.ConfigurationOverrideTransitionCommand(revision,expectedVersion); }
}
