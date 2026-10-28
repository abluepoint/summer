package com.abluepoint.summer.sample.configuration.application.command;
@org.springframework.modulith.NamedInterface("commands")
public record ConfigurationOverrideSaveCommand(Object value,Boolean publicReadable,Boolean enabled,Long expectedVersion) {}
