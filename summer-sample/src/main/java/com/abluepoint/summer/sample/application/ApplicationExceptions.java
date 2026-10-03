package com.abluepoint.summer.sample.application;

final class ApplicationConflictException extends RuntimeException { ApplicationConflictException(String m){super(m);} }
final class ApplicationValidationException extends RuntimeException { ApplicationValidationException(String m){super(m);} }
final class ApplicationPreconditionException extends RuntimeException { ApplicationPreconditionException(String m){super(m);} }
final class ApplicationIdempotencyConflictException extends RuntimeException { ApplicationIdempotencyConflictException(String m){super(m);} }
