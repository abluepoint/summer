package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_authorization_role")
public class AuthorizationRoleEntity {
    @Id @Column(name = "role_id", nullable = false, length = 96) private String roleId;
    @Column(name = "enterprise_id", nullable = false, length = 64) private String enterpriseId;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "code", nullable = false, length = 128) private String code;
    @Column(name = "built_in", nullable = false) private boolean builtIn;
    @Column(name = "status", nullable = false, length = 32) private String status;
    @Column(name = "capability_ids", nullable = false, columnDefinition = "text") private String capabilityIds;
    @Column(name = "capability_codes", nullable = false, columnDefinition = "text") private String capabilityCodes;
    @Column(name = "bundle_id", length = 96) private String bundleId;
    @Column(name = "version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AuthorizationRoleEntity() {}
    public AuthorizationRoleEntity(String id, String enterpriseId, String name, String code, boolean builtIn, String status, String ids, String codes, String bundleId, long version) {
        this.roleId=id; this.enterpriseId=enterpriseId; this.name=name; this.code=code; this.builtIn=builtIn; this.status=status; this.capabilityIds=ids; this.capabilityCodes=codes; this.bundleId=bundleId; this.version=version; this.createdAt=Instant.now(); this.updatedAt=this.createdAt;
    }
    public void update(String name, String code, String status, String ids, String codes, String bundleId, long version) { this.name=name; this.code=code; this.status=status; this.capabilityIds=ids; this.capabilityCodes=codes; this.bundleId=bundleId; this.version=version; this.updatedAt=Instant.now(); }
    public String roleId(){return roleId;} public String enterpriseId(){return enterpriseId;} public String name(){return name;} public String code(){return code;} public boolean builtIn(){return builtIn;} public String status(){return status;} public String capabilityIds(){return capabilityIds;} public String capabilityCodes(){return capabilityCodes;} public String bundleId(){return bundleId;} public long version(){return version;}
}
