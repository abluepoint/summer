package com.abluepoint.summer.sample.plugin.application;
import com.abluepoint.summer.sample.plugin.domain.PluginStatus;
import java.util.List;
import java.util.Map;
public record PluginDiagnosticView(String pluginId, String version, boolean trusted, boolean compatible,
                            PluginStatus status, String diagnostic, List<String> problems) {}
