package com.abluepoint.summer.sample.connection;

final class ConnectionValidationException extends RuntimeException { ConnectionValidationException(String message) { super(message); } }
final class ConnectionConflictException extends RuntimeException { ConnectionConflictException(String message) { super(message); } }
final class ConnectionPreconditionException extends RuntimeException { ConnectionPreconditionException(String message) { super(message); } }
final class ConnectionIdempotencyConflictException extends RuntimeException { ConnectionIdempotencyConflictException(String message) { super(message); } }
