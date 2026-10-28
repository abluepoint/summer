package com.abluepoint.summer.sample.release.application;

import java.util.List;
public record RollbackDryRunView(boolean dryRun, String wouldSwitchFrom, String wouldSwitchTo, String currentRelease) {}
