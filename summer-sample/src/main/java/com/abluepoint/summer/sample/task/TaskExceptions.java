package com.abluepoint.summer.sample.task;

final class TaskValidationException extends RuntimeException { TaskValidationException(String m) { super(m); } }
final class TaskUntrustedTypeException extends RuntimeException { TaskUntrustedTypeException(String t) { super("Task type is not trusted: " + t); } }
final class TaskNotFoundException extends RuntimeException { TaskNotFoundException() { super("Task not found"); } }
final class TaskConflictException extends RuntimeException { TaskConflictException(String m) { super(m); } }
final class TaskIdempotencyConflictException extends RuntimeException { TaskIdempotencyConflictException(String m) { super(m); } }
final class TaskIdempotencyRequiredException extends RuntimeException { TaskIdempotencyRequiredException() { super("Idempotency-Key is required"); } }
final class TaskPreconditionException extends RuntimeException { TaskPreconditionException() { super("The task was changed by another request"); } }
