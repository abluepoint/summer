package com.abluepoint.summer.sample.authorization.application;

/** Use-case failure; the API alone chooses the HTTP status. */
public final class OAuthClientManagementException extends RuntimeException {
    public enum Reason {
        INVALID("SUMMER_OAUTH_CLIENT_INVALID"), EXISTS("SUMMER_OAUTH_CLIENT_EXISTS"),
        VERSION_CONFLICT("SUMMER_OAUTH_CLIENT_VERSION_CONFLICT"), NOT_FOUND("SUMMER_OAUTH_CLIENT_NOT_FOUND"),
        CONSENT_NOT_FOUND("SUMMER_OAUTH_CONSENT_NOT_FOUND");
        private final String code;
        Reason(String code) { this.code = code; }
        public String code() { return code; }
    }
    private final Reason reason;
    public OAuthClientManagementException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
