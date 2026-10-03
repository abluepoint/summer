package com.abluepoint.summer.sample.enterprise;

final class ReviewPreconditionException extends RuntimeException {}
final class ReviewConflictException extends RuntimeException { ReviewConflictException(String message) { super(message); } }
final class ReviewValidationException extends RuntimeException { ReviewValidationException(String message) { super(message); } }
