package com.abluepoint.summer.sample.dictionary.infrastructure;

import static com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve;

public record DictionaryEvent(String eventId, String tenantId, String dictionaryCode, String eventType,
                       long version, String requestId) {}
