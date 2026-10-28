package com.abluepoint.summer.sample.configuration.application.view;
@org.springframework.modulith.NamedInterface("views")
public record ConfigurationOverrideView(String resource, String key, String organizationId, boolean publicReadable, boolean enabled, long version, long revision, Long publishedRevision, String state) {}
