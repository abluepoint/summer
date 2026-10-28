package com.abluepoint.summer.sample.plugin.infrastructure;
import com.abluepoint.summer.sample.plugin.domain.PluginDescriptor;
import com.abluepoint.summer.sample.plugin.domain.SummerPlugin;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public final class PluginCatalog implements com.abluepoint.summer.sample.plugin.domain.TrustedPluginCatalog {
    private final Map<String, SummerPlugin> plugins;

    public PluginCatalog(List<SummerPlugin> plugins) {
        this.plugins = plugins.stream().collect(Collectors.toUnmodifiableMap(
                plugin -> plugin.descriptor().pluginId(), Function.identity()));
    }

    public PluginDescriptor find(String pluginId) { return plugins.get(pluginId) == null ? null : plugins.get(pluginId).descriptor(); }
    public List<PluginDescriptor> all() { return plugins.values().stream().map(SummerPlugin::descriptor).toList(); }
}
