package com.abluepoint.summer.sample.enterprise;

final class PlatformEnterpriseExceptions {
    private PlatformEnterpriseExceptions() {}
}

class PlatformEnterpriseNotFoundException extends RuntimeException {
    PlatformEnterpriseNotFoundException(String id) {
        super("Enterprise not found: " + id);
    }
}

class PlatformEnterprisePreconditionException extends RuntimeException {}

class PlatformEnterpriseConflictException extends RuntimeException {
    PlatformEnterpriseConflictException(String message) {
        super(message);
    }
}

class PlatformEnterpriseValidationException extends RuntimeException {
    PlatformEnterpriseValidationException(String message) {
        super(message);
    }
}
