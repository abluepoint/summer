package com.abluepoint.summer.sample.plugin.domain;
import java.util.List;
/** Trusted descriptors come from application-shipped artifacts, never arbitrary uploads. */
public interface TrustedPluginCatalog { PluginDescriptor find(String pluginId); List<PluginDescriptor> all(); }
