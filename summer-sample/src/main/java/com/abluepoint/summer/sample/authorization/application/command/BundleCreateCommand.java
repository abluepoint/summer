package com.abluepoint.summer.sample.authorization.application.command;

import java.util.List;

public record BundleCreateCommand(String name, String code, List<String> capabilityIds, String changeSummary) {}
