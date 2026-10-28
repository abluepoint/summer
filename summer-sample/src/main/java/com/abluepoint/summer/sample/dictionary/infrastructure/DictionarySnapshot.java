package com.abluepoint.summer.sample.dictionary.infrastructure;

import com.abluepoint.summer.sample.dictionary.application.view.DictionaryItemView;

import static com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve;
import java.util.List;

public record DictionarySnapshot(String name, boolean publicReadable, boolean enabled, List<DictionaryItemView> items) {}
