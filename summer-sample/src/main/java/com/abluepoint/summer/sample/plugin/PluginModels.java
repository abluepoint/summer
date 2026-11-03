package com.abluepoint.summer.sample.plugin;

import java.util.List;
import java.util.Map;

record PluginRegisterRequest(String pluginId, String version, String artifactDigest) {}
record PluginUpgradeRequest(String version, String artifactDigest) {}
record PluginView(String pluginId, String displayName, String version, String artifactDigest,
                  PluginStatus status, boolean trusted, String diagnostic, long revision,
                  List<String> capabilities, List<PluginMenu> menus,
                  List<PluginConfiguration> configuration, String apiBasePath,
                  List<String> dataResources) {}
record PluginPage(List<PluginView> items, long revision) {}
record PluginDiagnosticView(String pluginId, String version, boolean trusted, boolean compatible,
                            PluginStatus status, String diagnostic, List<String> problems) {}
record PluginDictionaryView(String code, List<Map<String, Object>> items) {}
record PluginInsightsView(String pluginId, String version, String requestId, long activeCustomerCount,
                          List<Map<String, Object>> configuration, PluginDictionaryView dictionary,
                          long auditRevision) {}

enum PluginStatus { REGISTERED, ENABLED, DISABLED, INCOMPATIBLE }

