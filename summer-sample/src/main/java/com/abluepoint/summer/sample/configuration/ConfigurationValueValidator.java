package com.abluepoint.summer.sample.configuration;
import org.springframework.security.core.Authentication;
/** Validates the current base definition and data scope at every override lifecycle transition. */
public interface ConfigurationValueValidator {
    String resource();
    void requireBase(String tenant, String key, Authentication auth);
    void validate(String tenant, String key, Object value, Authentication auth);
}
