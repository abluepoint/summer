package com.abluepoint.summer.sample.identity;

final class SessionIndexUnavailableException extends RuntimeException {
    SessionIndexUnavailableException() { super("Shared session storage is unavailable"); }
}

final class SessionReferenceInvalidException extends RuntimeException {
    SessionReferenceInvalidException() { super("The session reference must be a 64-character hexadecimal value"); }
}

final class SessionNotFoundException extends RuntimeException {
    SessionNotFoundException() { super("The session was not found"); }
}

final class SessionRevokedException extends RuntimeException {
    SessionRevokedException() { super("The server session has been revoked"); }
}
