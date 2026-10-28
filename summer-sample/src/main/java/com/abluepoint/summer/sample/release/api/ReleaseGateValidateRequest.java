package com.abluepoint.summer.sample.release.api;
import java.util.List;
import com.abluepoint.summer.sample.release.application.ReleaseGateValidateCommand;
public record ReleaseGateValidateRequest(String releaseVersion, Boolean dryRun, List<String> requiredChecks) { public ReleaseGateValidateCommand toCommand() { return new ReleaseGateValidateCommand(releaseVersion,dryRun,requiredChecks); } }
