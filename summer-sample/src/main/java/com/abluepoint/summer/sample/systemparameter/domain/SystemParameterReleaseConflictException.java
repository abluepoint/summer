package com.abluepoint.summer.sample.systemparameter.domain;

import static com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve;

public final class SystemParameterReleaseConflictException extends RuntimeException {
    public SystemParameterReleaseConflictException(String message) { super(message); }
}
