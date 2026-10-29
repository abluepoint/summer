package com.abluepoint.summer.sample.systemparameter.domain;

public final class SystemParameterEventNotFoundException extends RuntimeException {
    public SystemParameterEventNotFoundException(String eventId) { super("System parameter event not found: " + eventId); }
}
