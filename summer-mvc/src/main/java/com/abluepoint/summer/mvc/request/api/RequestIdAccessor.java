package com.abluepoint.summer.mvc.request.api;

@FunctionalInterface
public interface RequestIdAccessor {

    String currentRequestId();
}
