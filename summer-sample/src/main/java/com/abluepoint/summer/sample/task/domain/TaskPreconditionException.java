package com.abluepoint.summer.sample.task.domain;

public final class TaskPreconditionException extends RuntimeException { public TaskPreconditionException() { super("The task was changed by another request"); } }
