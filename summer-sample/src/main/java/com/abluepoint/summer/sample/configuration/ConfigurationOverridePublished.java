package com.abluepoint.summer.sample.configuration;

/** In-transaction invalidation metadata; draft saves never publish this event. */
public record ConfigurationOverridePublished(String tenantId, String resource, String key,
                                             String state, String requestId) {}
