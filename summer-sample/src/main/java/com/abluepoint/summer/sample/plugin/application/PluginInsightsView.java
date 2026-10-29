package com.abluepoint.summer.sample.plugin.application;

import java.util.List;
import java.util.Map;
public record PluginInsightsView(String pluginId, String version, String requestId, long activeCustomerCount,
                          List<Map<String, Object>> configuration, PluginDictionaryView dictionary,
                          long auditRevision) {}
