package com.abluepoint.summer.sample.plugin.api;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.plugin.application.PluginUpgradeCommand;
public record PluginUpgradeRequest(String version, String artifactDigest) { public PluginUpgradeCommand toCommand() { return new PluginUpgradeCommand(version,artifactDigest); } }
