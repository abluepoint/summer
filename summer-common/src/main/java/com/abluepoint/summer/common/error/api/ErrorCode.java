package com.abluepoint.summer.common.error.api;

/** Stable cross-module error contract. */
public interface ErrorCode {

    String code();

    String defaultMessage();

    ErrorCategory category();
}
