package com.abluepoint.summer.sample.configuration.infrastructure;

import com.abluepoint.summer.sample.authorization.infrastructure.EnterpriseRequestContext;
import org.springframework.security.core.Authentication;
import java.util.Set;

/** Configuration-facing facade; request identity selection belongs to authorization. */
@org.springframework.modulith.NamedInterface("context")
public final class ConfigurationTenantContext {
    private ConfigurationTenantContext() {}
    public static String resolve(Authentication authentication) {
        return EnterpriseRequestContext.resolve(authentication);
    }
    public static void requireMembership(String tenant, Authentication authentication) {
        EnterpriseRequestContext.requireMembership(tenant, authentication);
    }
    public static Set<String> activeMemberships(Authentication authentication) {
        return EnterpriseRequestContext.activeMemberships(authentication);
    }
}
