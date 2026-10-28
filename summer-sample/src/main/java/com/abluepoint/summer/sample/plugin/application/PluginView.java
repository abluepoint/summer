package com.abluepoint.summer.sample.plugin.application;
import com.abluepoint.summer.sample.plugin.domain.PluginStatus;
import com.abluepoint.summer.sample.plugin.domain.PluginConfiguration;
import com.abluepoint.summer.sample.plugin.domain.PluginMenu;
import java.util.List;
import java.util.Map;
public record PluginView(String pluginId, String displayName, String version, String artifactDigest,
                  PluginStatus status, boolean trusted, String diagnostic, long revision,
                  List<String> capabilities, List<PluginMenu> menus,
                  List<PluginConfiguration> configuration, String apiBasePath,
                  List<String> dataResources) {}
