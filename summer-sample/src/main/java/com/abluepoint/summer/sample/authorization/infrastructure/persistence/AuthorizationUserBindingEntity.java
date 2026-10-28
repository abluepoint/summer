package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "summer_authorization_user_binding",
        uniqueConstraints = @UniqueConstraint(name = "uk_summer_authorization_user_binding_user", columnNames = {"enterprise_id", "username"}))
public class AuthorizationUserBindingEntity {
    @Id
    @Column(name = "binding_id", nullable = false, length = 96)
    private String bindingId;
    @Column(name = "enterprise_id", nullable = false, length = 64)
    private String enterpriseId;
    @Column(name = "username", nullable = false, length = 128)
    private String username;
    @Column(name = "role_ids", nullable = false, columnDefinition = "text")
    private String roleIds;
    @Column(name = "denied_capability_codes", nullable = false, columnDefinition = "text")
    private String deniedCapabilityCodes;
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthorizationUserBindingEntity() {}

    public AuthorizationUserBindingEntity(String bindingId, String enterpriseId, String username,
                                   String roleIds, String deniedCapabilityCodes, long version) {
        this.bindingId = bindingId;
        this.enterpriseId = enterpriseId;
        this.username = username;
        this.roleIds = roleIds;
        this.deniedCapabilityCodes = deniedCapabilityCodes;
        this.version = version;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String roleIds, String deniedCapabilityCodes, long version) {
        this.roleIds = roleIds;
        this.deniedCapabilityCodes = deniedCapabilityCodes;
        this.version = version;
        this.updatedAt = Instant.now();
    }
    public String bindingId() { return bindingId; }
    public String enterpriseId() { return enterpriseId; }
    public String username() { return username; }
    public String roleIds() { return roleIds; }
    public String deniedCapabilityCodes() { return deniedCapabilityCodes; }
    public long version() { return version; }
}
