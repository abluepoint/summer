package com.abluepoint.summer.sample.configuration.api.request;

import com.abluepoint.summer.sample.configuration.application.command.ConfigurationOverrideSaveCommand;
@org.springframework.modulith.NamedInterface("requests")
public record ConfigurationOverrideRequest(Object value, Boolean publicReadable, Boolean enabled, Long expectedVersion) {
    public com.abluepoint.summer.sample.configuration.application.command.ConfigurationOverrideSaveCommand toCommand() { return new com.abluepoint.summer.sample.configuration.application.command.ConfigurationOverrideSaveCommand(value,publicReadable,enabled,expectedVersion); }
}
