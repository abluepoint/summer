package com.abluepoint.summer.sample.release;

final class ReleaseValidationException extends RuntimeException { ReleaseValidationException(String m) { super(m); } }
final class ReleaseIdempotencyConflictException extends RuntimeException { ReleaseIdempotencyConflictException(String m) { super(m); } }
