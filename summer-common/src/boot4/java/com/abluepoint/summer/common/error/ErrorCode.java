package com.abluepoint.summer.common.error;

/** Stable cross-module error contract. */
public interface ErrorCode {

    String code();

    String defaultMessage();
}
