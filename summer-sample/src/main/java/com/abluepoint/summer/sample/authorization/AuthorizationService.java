package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedConsumerAccess;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Narrow compatibility facade: old grant APIs are retired; current resource gates remain. */
@Service
@org.springframework.context.annotation.DependsOn("flywayMigrationInitializer")
public class AuthorizationService {
    private final ManagedConsumerAccess managedAccess;
    private final JdbcTemplate membershipJdbc;
    private final AuthorizationAuditStore auditStore;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthorizationService(ManagedConsumerAccess access,JdbcTemplate db,AuthorizationAuditStore audit) {
        managedAccess=access; membershipJdbc=db; auditStore=audit;
    }

    /** Retain source compatibility for callers constructing the former adapter; no old policy. */
    public AuthorizationService(AuthorizationUserBindingRepository bindings,AuthorizationCapabilityRepository capabilities,
            AuthorizationRoleRepository roles,AuthorizationChangeEventService events,AuthorizationAuditStore audit,JdbcTemplate db) {
        this(null,db,audit);
    }
    public AuthorizationService(AuthorizationUserBindingRepository bindings,AuthorizationCapabilityRepository capabilities,
            AuthorizationRoleRepository roles,AuthorizationChangeEventService events,AuthorizationAuditStore audit,JdbcTemplate db,
            ManagedConsumerAccess access) {
        this(access,db,audit);
    }

    void loadUserAssignments() { /* No legacy projection is loaded in the target runtime. */ }
    public void reloadPersistentState() { /* Current managed facts are read from the authority. */ }

    /** Old code/deny/bundle decisions can never supply a second ALLOW. */
    public Decision decision(Authentication actor,String enterpriseId,String oldCode,String requestId) {
        return new Decision(false,"CONTROLLED_MANAGED_UPGRADE_REQUIRED");
    }
    public Decision platformDecision(Authentication actor,String oldCode,String requestId) {
        return new Decision(false,"CONTROLLED_MANAGED_UPGRADE_REQUIRED");
    }
    public record Decision(boolean allowed,String reasonCode) {}

    public void requireEnterpriseMenu(String enterpriseId,Authentication actor,String scope) {
        if(managedAccess==null || !managedAccess.require(Domain.enterprise(enterpriseId),actor,scope))throw denied();
    }
    public void requirePlatformMenu(Authentication actor) {
        if(managedAccess==null || !managedAccess.require(Domain.platform(),actor,"platform.menu.write"))throw denied();
    }
    public void requireEnterpriseMembership(String enterpriseId,Authentication actor) {
        if(enterpriseId==null || !enterpriseId.matches("[A-Za-z0-9_-]{1,80}")
                || actor==null || !actor.isAuthenticated() || managedAccess==null
                || !managedAccess.managed(Domain.enterprise(enterpriseId))
                || membershipJdbc.queryForObject("select count(*) from summer_enterprise_member m join summer_enterprise e on e.enterprise_id=m.enterprise_id join summer_auth_user u on u.username=m.subject where m.enterprise_id=? and m.subject=? and m.status='ACTIVE' and e.status='ACTIVE' and u.active=true",Integer.class,enterpriseId,actor.getName())!=1)
            throw denied();
    }
    synchronized List<Map<String,Object>> audit(String enterpriseId,String id,Authentication actor) {
        requireEnterpriseMenu(enterpriseId,actor,"enterprise.roles.read");
        return auditStore.read(Domain.enterprise(enterpriseId),id);
    }
    public Long legacyRoleVersion(Object value) { return null; }
    private static AccessDeniedException denied() {return new AccessDeniedException("Current managed resource permission required");}
    private static SummerException retired() {return SummerException.withMessage(AuthorizationErrorCode.INVALID,
        "Legacy code, deny and bundle grant APIs are retired; use permission and role management");}

    synchronized CapabilityCatalogView capabilities(String enterpriseId, Authentication auth) { throw retired(); }
    synchronized CapabilityCatalogView platformCapabilities(Authentication auth) { throw retired(); }
    synchronized CapabilityView createCapability(String e, CapabilityCreateRequest req, String key, String rid, Authentication a) { throw retired(); }
    synchronized CapabilityView createPlatformCapability(CapabilityCreateRequest req, String key, String rid, Authentication a) { throw retired(); }
    synchronized CapabilityView setCapabilityStatus(String e, String id, String status, String ifm, String key, String rid, Authentication a) { throw retired(); }
    synchronized List<RoleView> roles(String e, Authentication a) { throw retired(); }
    synchronized RoleView role(String e, String id, Authentication a) { throw retired(); }
    synchronized RoleView createRole(String e, RoleCreateRequest req, String key, String rid, Authentication a) { throw retired(); }
    synchronized RoleView updateRole(String e, String id, RoleUpdateRequest req, String ifm, String key, String rid, Authentication a) { throw retired(); }
    synchronized RoleView setRoleCapabilities(String e, String id, RoleCapabilitiesRequest req, String ifm, String key, String rid, Authentication a) { throw retired(); }
    synchronized List<BundleView> bundles(String e, Authentication a) { throw retired(); }
    synchronized BundleView bundle(String e, String id, Authentication a) { throw retired(); }
    synchronized BundleView createBundle(String e, BundleCreateRequest req, String key, String rid, Authentication a) { throw retired(); }
    synchronized BundleView updateBundle(String e, String id, BundleUpdateRequest req, String ifm, String key, String rid, Authentication a) { throw retired(); }
    synchronized List<BundleVersionView> bundleVersions(String e, String id, Authentication a) { throw retired(); }
    synchronized UserRoleAssignmentView assignUserRoles(String enterpriseId, String username,
                                                         UserRoleAssignmentRequest request, String ifMatch,
                                                         String key, String requestId, Authentication authentication) { throw retired(); }
    synchronized UserRoleAssignmentView userAssignment(String enterpriseId, String username, Authentication authentication) { throw retired(); }
    public Object legacyRoleRequest(String e, Authentication auth, String method, String target, String id,
                                    Map<String,Object> body, String condition, String key, String rid) { throw retired(); }
}
