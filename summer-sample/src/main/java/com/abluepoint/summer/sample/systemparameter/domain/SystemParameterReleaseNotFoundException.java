package com.abluepoint.summer.sample.systemparameter.domain;


public final class SystemParameterReleaseNotFoundException extends RuntimeException {
    public SystemParameterReleaseNotFoundException(String key, long revision) { super("System parameter version not found: " + key + ":" + revision); }
}
