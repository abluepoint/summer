package com.abluepoint.summer.sample.menu.application.command;

public record MenuManifestOverrideCommand(Boolean enabled, String parentMenuId, Integer sortOrder,
                                   String capabilityCode) {}
