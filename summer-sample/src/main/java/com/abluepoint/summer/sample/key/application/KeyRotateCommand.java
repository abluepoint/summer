package com.abluepoint.summer.sample.key.application;

public record KeyRotateCommand(int retentionDays, int expectedVersion, String idempotencyKey) {}
