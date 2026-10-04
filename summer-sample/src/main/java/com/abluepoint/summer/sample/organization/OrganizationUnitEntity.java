package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_organization_unit", uniqueConstraints = @UniqueConstraint(name = "uk_summer_org_unit_id", columnNames = "unit_id"))
class OrganizationUnitEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64) private String unitId;
    @Column(nullable = false, length = 64) private String enterpriseId;
    @Column(length = 64) private String parentId;
    @Column(nullable = false, length = 32) private String unitType;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 64) private String code;
    @Column(length = 64) private String leaderMemberId;
    @Column(length = 500) private String description;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false, length = 32) private String status;
    protected OrganizationUnitEntity() {}
    OrganizationUnitEntity(String unitId, String enterpriseId, String parentId, String unitType, String name, String code, String leaderMemberId, String description, int sortOrder, String status) {
        this.unitId = unitId; this.enterpriseId = enterpriseId; this.parentId = parentId; this.unitType = unitType; this.name = name; this.code = code; this.leaderMemberId = leaderMemberId; this.description = description; this.sortOrder = sortOrder; this.status = status;
    }
    String unitId() { return unitId; }
}
