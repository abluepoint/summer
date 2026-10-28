package com.abluepoint.summer.sample.plugin.api;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.plugin.application.PluginRegisterCommand;
public record PluginRegisterRequest(String pluginId, String version, String artifactDigest) { public PluginRegisterCommand toCommand() { return new PluginRegisterCommand(pluginId,version,artifactDigest); } }
