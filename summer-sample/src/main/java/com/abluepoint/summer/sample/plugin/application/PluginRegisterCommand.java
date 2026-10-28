package com.abluepoint.summer.sample.plugin.application;


import java.util.List;
import java.util.Map;

public record PluginRegisterCommand(String pluginId, String version, String artifactDigest) {}
