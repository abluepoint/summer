package com.abluepoint.summer.sample.delegation;
final class DelegationValidationException extends RuntimeException { DelegationValidationException(String m){super(m);} }
final class DelegationConflictException extends RuntimeException { DelegationConflictException(String m){super(m);} }
final class DelegationIdempotencyConflictException extends RuntimeException { DelegationIdempotencyConflictException(String m){super(m);} }
