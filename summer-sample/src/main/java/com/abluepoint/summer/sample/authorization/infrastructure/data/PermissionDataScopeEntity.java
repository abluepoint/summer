package com.abluepoint.summer.sample.authorization.infrastructure.data;

import jakarta.persistence.*;

/** Schema mapping for Hibernate-managed local profiles; production evolves through Flyway. */
@Entity
@IdClass(PermissionDataScopeEntity.Key.class)
@Table(name = "summer_permission_data_scope")
class PermissionDataScopeEntity {
    @Id
    @Column(name = "permission_id", length = 96)
    private String permissionId;

    @Id
    @Column(name = "domain_key", nullable = false, length = 96)
    private String domainKey;

    @Column(name = "data_resource_key", length = 96)
    private String dataResourceKey;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "scope_preset", nullable = false, length = 32)
    private String scopePreset;

    public record Key(String permissionId, String domainKey) implements java.io.Serializable {}

    protected PermissionDataScopeEntity() {}
}
