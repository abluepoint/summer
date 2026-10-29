package com.abluepoint.summer.sample.systemparameter.domain;

import static com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve;

public final class SystemParameterReleaseNotFoundException extends RuntimeException {
    public SystemParameterReleaseNotFoundException(String key, long revision) { super("System parameter version not found: " + key + ":" + revision); }
}
