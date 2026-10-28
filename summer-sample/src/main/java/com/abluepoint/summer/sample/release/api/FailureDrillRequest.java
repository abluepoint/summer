package com.abluepoint.summer.sample.release.api;
import java.util.List;
import com.abluepoint.summer.sample.release.application.FailureDrillCommand;
public record FailureDrillRequest(String component, String scenario) { public FailureDrillCommand toCommand() { return new FailureDrillCommand(component,scenario); } }
