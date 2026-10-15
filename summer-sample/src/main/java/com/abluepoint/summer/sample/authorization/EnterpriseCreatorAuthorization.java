package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.enterprise.EnterpriseCreated;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Establishes the first tenant administrator without any platform-wide business grant. */
@Component
class EnterpriseCreatorAuthorization {
    private static final List<String> CODES = List.of("enterprise.organization.read", "enterprise.organization.write",
            "enterprise.members.read", "enterprise.members.write", "enterprise.roles.read", "enterprise.roles.write",
            "enterprise.capabilities.read");
    private final AuthorizationCapabilityRepository capabilities;
    private final AuthorizationRoleRepository roles;
    private final AuthorizationUserBindingRepository bindings;
    private final AuthorizationAuditStore audit;
    private final AuthorizationChangeEventService changes;
    EnterpriseCreatorAuthorization(AuthorizationCapabilityRepository capabilities, AuthorizationRoleRepository roles,
            AuthorizationUserBindingRepository bindings, AuthorizationAuditStore audit, AuthorizationChangeEventService changes) {
        this.capabilities = capabilities; this.roles = roles; this.bindings = bindings; this.audit = audit; this.changes = changes;
    }
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(EnterpriseCreated event) {
        var ids = new ArrayList<String>();
        for (String code : CODES) {
            String id = "cap-" + UUID.randomUUID();
            capabilities.save(new AuthorizationCapabilityEntity(id, event.enterpriseId(), "ENTERPRISE", code, code,
                    code.substring(code.lastIndexOf('.') + 1), "HIGH", "ACTIVE", 1));
            ids.add(id);
        }
        String role = "role-" + UUID.randomUUID();
        roles.save(new AuthorizationRoleEntity(role, event.enterpriseId(), "Tenant administrator", "tenant-administrator", false,
                "ACTIVE", String.join("\n", ids), String.join("\n", CODES), null, 1));
        String jsonRoles = new tools.jackson.databind.ObjectMapper().writeValueAsString(List.of(role));
        bindings.saveAndFlush(new AuthorizationUserBindingEntity("binding-" + UUID.randomUUID(), event.enterpriseId(), event.creatorSubject(), jsonRoles, "[]", 1));
        audit.mutation(event.enterpriseId(), role, "TENANT_ADMINISTRATOR_CREATED", event.requestId(), event.creatorSubject());
        changes.enqueue(event.enterpriseId(), "ROLE", role, "TENANT_ADMINISTRATOR_CREATED", 1, event.requestId());
    }
}
