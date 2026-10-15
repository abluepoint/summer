package com.abluepoint.summer.sample.configuration;
public record ConfigurationOverrideView(String resource, String key, String organizationId, boolean publicReadable, boolean enabled, long version, long revision, Long publishedRevision, String state) {}
