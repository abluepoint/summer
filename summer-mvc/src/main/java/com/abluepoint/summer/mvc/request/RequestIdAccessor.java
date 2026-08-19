package com.abluepoint.summer.mvc.request;

@FunctionalInterface
public interface RequestIdAccessor {

    String currentRequestId();
}
