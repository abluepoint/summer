package com.abluepoint.summer.sample.plugin;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
final class PluginCatalog {
    private final Map<String, SummerPlugin> plugins;

    PluginCatalog(List<SummerPlugin> plugins) {
        this.plugins = plugins.stream().collect(Collectors.toUnmodifiableMap(
                plugin -> plugin.descriptor().pluginId(), Function.identity()));
    }

    PluginDescriptor find(String pluginId) { return plugins.get(pluginId) == null ? null : plugins.get(pluginId).descriptor(); }
    List<PluginDescriptor> all() { return plugins.values().stream().map(SummerPlugin::descriptor).toList(); }
}
