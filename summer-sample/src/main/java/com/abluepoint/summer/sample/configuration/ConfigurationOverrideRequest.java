package com.abluepoint.summer.sample.configuration;
public record ConfigurationOverrideRequest(Object value, Boolean publicReadable, Boolean enabled, Long expectedVersion) {}
