package com.abluepoint.summer.sample.task.domain;

public final class TaskUntrustedTypeException extends RuntimeException { public TaskUntrustedTypeException(String t) { super("Task type is not trusted: " + t); } }
