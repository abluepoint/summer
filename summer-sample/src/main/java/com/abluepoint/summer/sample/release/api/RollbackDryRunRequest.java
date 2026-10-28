package com.abluepoint.summer.sample.release.api;
import java.util.List;
import com.abluepoint.summer.sample.release.application.RollbackDryRunCommand;
public record RollbackDryRunRequest(String releaseVersion, String targetVersion, String reason) { public RollbackDryRunCommand toCommand() { return new RollbackDryRunCommand(releaseVersion,targetVersion,reason); } }
