package com.abluepoint.summer.sample.plugin.application;

import java.util.List;
import java.util.Map;
public record PluginDictionaryView(String code, List<Map<String, Object>> items) {}
