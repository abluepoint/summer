package com.abluepoint.summer.sample.release.application;

import java.util.List;
public record RollbackDryRunCommand(String releaseVersion, String targetVersion, String reason) {}
