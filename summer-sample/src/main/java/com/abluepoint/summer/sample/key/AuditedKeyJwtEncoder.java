package com.abluepoint.summer.sample.key;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

final class AuditedKeyJwtEncoder implements JwtEncoder {
    private final JwtEncoder delegate;
    private final KeyFailureAudit audit;

    AuditedKeyJwtEncoder(JwtEncoder delegate, KeyFailureAudit audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public Jwt encode(JwtEncoderParameters parameters) {
        try {
            return delegate.encode(parameters);
        } catch (RuntimeException failure) {
            audit.signFailed();
            throw failure;
        }
    }
}
