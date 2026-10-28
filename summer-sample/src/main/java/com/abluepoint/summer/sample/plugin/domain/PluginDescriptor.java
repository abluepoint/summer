package com.abluepoint.summer.sample.plugin.domain;


import java.util.List;

public record PluginDescriptor(String pluginId, String displayName, String version, String artifactDigest,
                               String requiredFrameworkVersion, List<String> capabilities,
                               List<PluginMenu> menus, List<PluginConfiguration> configuration,
                               String apiBasePath, List<String> dataResources) {
    public PluginDescriptor {
        capabilities = List.copyOf(capabilities);
        menus = List.copyOf(menus);
        configuration = List.copyOf(configuration);
        dataResources = List.copyOf(dataResources);
    }
}
