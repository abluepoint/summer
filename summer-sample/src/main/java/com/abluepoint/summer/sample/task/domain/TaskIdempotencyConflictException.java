package com.abluepoint.summer.sample.task.domain;

public final class TaskIdempotencyConflictException extends RuntimeException { public TaskIdempotencyConflictException(String m) { super(m); } }
