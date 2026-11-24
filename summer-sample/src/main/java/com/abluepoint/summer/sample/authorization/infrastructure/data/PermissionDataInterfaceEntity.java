package com.abluepoint.summer.sample.authorization.infrastructure.data;

import jakarta.persistence.*;

@Entity
@IdClass(PermissionDataInterfaceEntity.Key.class)
@Table(name = "summer_permission_data_interface")
class PermissionDataInterfaceEntity {
    @Id @Column(name = "domain_key", length = 96) private String domainKey;
    @Id @Column(name = "permission_id", length = 96) private String permissionId;
    @Id @Column(name = "interface_permission_id", length = 96) private String interfacePermissionId;

    public record Key(String domainKey, String permissionId, String interfacePermissionId)
            implements java.io.Serializable {}
}
