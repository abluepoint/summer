package com.abluepoint.summer.sample.configuration.application.port;
import org.springframework.security.core.Authentication;
/** Validates the current base definition and data scope at every override lifecycle transition. */
@org.springframework.modulith.NamedInterface("validation")
public interface ConfigurationValueValidator {
    public String resource();
    public void requireBase(String tenant, String key, Authentication auth);
    public void validate(String tenant, String key, Object value, Authentication auth);
}
