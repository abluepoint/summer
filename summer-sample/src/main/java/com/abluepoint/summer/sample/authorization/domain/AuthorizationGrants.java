package com.abluepoint.summer.sample.authorization.domain;

import java.util.Map;
import java.util.Objects;

/** Current role/capability status and owner boundaries are applied for every decision. */
public final class AuthorizationGrants {
    private AuthorizationGrants() {}
    public static boolean allows(AuthorizationAssignment assignment, String enterpriseId, String permissionCode,
                                 Map<String, AuthorizationRole> roles, Map<String, AuthorizationCapability> capabilities) {
        return assignment.roleIds.stream().map(roles::get).filter(Objects::nonNull)
                .filter(role -> enterpriseId.equals(role.enterpriseId) && "ACTIVE".equals(role.status))
                .flatMap(role -> role.capabilityIds.stream()).map(id -> capabilities.get(id))
                .filter(Objects::nonNull).filter(capability -> "ACTIVE".equals(capability.status)
                        && (capability.enterpriseId == null || enterpriseId.equals(capability.enterpriseId)))
                .anyMatch(capability -> permissionCode.equals(capability.code) || permissionCode.equals(capability.resource));
    }
    public static boolean allowsPlatform(AuthorizationAssignment binding, String permissionCode,
                                         Map<String, AuthorizationRole> roles, Map<String, AuthorizationCapability> capabilities) {
        return binding.roleIds.stream().map(roles::get).filter(Objects::nonNull)
                    .filter(role -> binding.enterpriseId.equals(role.enterpriseId) && "ACTIVE".equals(role.status))
                    .flatMap(role -> role.capabilityIds.stream()).map(capabilities::get).filter(Objects::nonNull)
                    .anyMatch(capability -> "ACTIVE".equals(capability.status) && "PLATFORM".equals(capability.scope)
                            && capability.enterpriseId == null && permissionCode.equals(capability.code));
    }
}
