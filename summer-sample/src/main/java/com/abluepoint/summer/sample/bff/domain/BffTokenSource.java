package com.abluepoint.summer.sample.bff.domain;

@FunctionalInterface
public interface BffTokenSource {
    String next();
}
