package com.abluepoint.summer.sample.systemparameter.application;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterNotFoundException;

import com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.Optional;

/** Narrow plugin-facing entry point with the same checks as the public HTTP API. */
@Service
@org.springframework.modulith.NamedInterface("published")
public class PublishedParameterReader {
    public record Value(Object value, long version) {}
    private final SystemParameterService parameters;
    public PublishedParameterReader(SystemParameterService parameters) { this.parameters = parameters; }
    public Optional<Value> read(String key, Authentication auth) { return read(com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve(auth), key, auth); }
    public Optional<Value> read(String tenant, String key, Authentication auth) {
        com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.requireMembership(tenant, auth);
        try {
            var value = parameters.publicRead(tenant, key, auth);
            return Optional.of(new Value(value.value(), value.version()));
        } catch (SystemParameterNotFoundException absent) {
            return Optional.empty();
        } catch (com.abluepoint.summer.common.error.api.SummerException failure) {
            if (failure.errorCode().category() == com.abluepoint.summer.common.error.api.ErrorCategory.NOT_FOUND) return Optional.empty();
            throw failure;
        }
    }
}
