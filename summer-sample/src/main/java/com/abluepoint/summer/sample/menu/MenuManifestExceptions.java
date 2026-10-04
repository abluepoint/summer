package com.abluepoint.summer.sample.menu;

class MenuManifestValidationException extends RuntimeException {
    MenuManifestValidationException(String message) { super(message); }
}
class MenuManifestConflictException extends RuntimeException {
    MenuManifestConflictException(String message) { super(message); }
}
class MenuManifestPreconditionException extends RuntimeException {
    MenuManifestPreconditionException(String message) { super(message); }
}
