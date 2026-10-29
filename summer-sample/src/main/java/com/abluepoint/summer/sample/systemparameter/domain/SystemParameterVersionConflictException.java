package com.abluepoint.summer.sample.systemparameter.domain;

public final class SystemParameterVersionConflictException extends RuntimeException {
    public SystemParameterVersionConflictException() {
        super("The system parameter was changed by another request");
    }
}
