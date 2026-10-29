package com.abluepoint.summer.sample.task.domain;

public final class TaskConflictException extends RuntimeException { public TaskConflictException(String m) { super(m); } public TaskConflictException(String m, Throwable cause) { super(m, cause); } }
