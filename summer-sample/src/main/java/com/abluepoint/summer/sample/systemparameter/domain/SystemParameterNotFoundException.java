package com.abluepoint.summer.sample.systemparameter.domain;

public final class SystemParameterNotFoundException extends RuntimeException {
    public SystemParameterNotFoundException(String key) {
        super("System parameter not found: " + key);
    }
}
