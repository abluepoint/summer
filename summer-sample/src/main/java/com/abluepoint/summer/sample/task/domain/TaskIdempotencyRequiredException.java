package com.abluepoint.summer.sample.task.domain;

public final class TaskIdempotencyRequiredException extends RuntimeException { public TaskIdempotencyRequiredException() { super("Idempotency-Key is required"); } }
