package com.abluepoint.summer.sample.agent.infrastructure;

import com.abluepoint.summer.sample.agent.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;

public final class AgentAccess {
    private AgentAccess() {}
    public static JwtAuthenticationToken requireRuntime(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)
                || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + "internal.agent_instances.write").equals(a.getAuthority()))) {
            throw new AccessDeniedException("Runtime scope denied");
        }
        return jwt;
    }

    public static void assertRuntime(AgentInstanceEntity instance, JwtAuthenticationToken runtime) {
        if (!AgentRules.matchesRuntime(instance.agentId, instance.runtimeId, instance.id, instance.workloadIdentity,
                claim(runtime, "agent_id"), claim(runtime, "runtime_id"), claim(runtime, "agent_instance_id"),
                claim(runtime, "workload_identity"), Boolean.TRUE.equals(runtime.getToken().getClaimAsBoolean("attestation_verified")))) {
            throw new AccessDeniedException("Runtime identity assertion mismatch");
        }
    }

    public static void requireAdmin(String enterpriseId, Authentication auth, String... capabilities) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || !AgentRules.permits(activeMembership(jwt, enterpriseId), hasCapability(jwt, enterpriseId, capabilities),
                Arrays.stream(capabilities).anyMatch(c -> jwt.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + c).equals(a.getAuthority()))))) {
            throw new AccessDeniedException("Agent capability denied");
        }
    }

    private static boolean activeMembership(JwtAuthenticationToken jwt, String enterpriseId) {
        Object memberships = jwt.getToken().getClaims().get("enterprise_memberships");
        return memberships instanceof Collection<?> values && values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(m -> enterpriseId.equals(String.valueOf(m.getOrDefault("enterpriseId", m.get("enterprise_id"))))
                        && "ACTIVE".equalsIgnoreCase(String.valueOf(m.getOrDefault("status", m.get("membership_status")))));
    }

    private static boolean hasCapability(JwtAuthenticationToken jwt, String enterpriseId, String... capabilities) {
        Object value = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(value instanceof Map<?, ?> map)) return false;
        Object granted = map.get(enterpriseId);
        return granted instanceof Collection<?> values && values.stream().map(String::valueOf).anyMatch(v -> Arrays.asList(capabilities).contains(v));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String claim(JwtAuthenticationToken jwt, String name) { return jwt.getToken().getClaimAsString(name); }
}
