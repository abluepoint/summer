package com.abluepoint.summer.sample.authorization.application.command;

import java.util.List;

public record BundleUpdateCommand(String name, String code, String status, List<String> capabilityIds, String changeSummary) {}
