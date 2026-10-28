package com.abluepoint.summer.sample.release.application;

import java.util.List;
public record ReleaseGateValidateCommand(String releaseVersion, Boolean dryRun, List<String> requiredChecks) {}
