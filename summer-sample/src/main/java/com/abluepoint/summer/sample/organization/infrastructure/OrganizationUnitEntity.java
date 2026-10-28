package com.abluepoint.summer.sample.organization.infrastructure;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_organization_unit", uniqueConstraints = @UniqueConstraint(name = "uk_summer_org_unit_id", columnNames = "unit_id"))
public class OrganizationUnitEntity extends BaseLongEntity {
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
    @org.hibernate.annotations.ColumnDefault("0")
    @jakarta.persistence.Version private long version;
    public long version() { return version; }
    public void update(String parentId, String unitType, String name, String code, String leaderMemberId, String description, int sortOrder, String status) {
        this.parentId = parentId; this.unitType = unitType; this.name = name; this.code = code; this.leaderMemberId = leaderMemberId; this.description = description; this.sortOrder = sortOrder; this.status = status;
    }
    protected OrganizationUnitEntity() {}
    public OrganizationUnitEntity(String unitId, String enterpriseId, String parentId, String unitType, String name, String code, String leaderMemberId, String description, int sortOrder, String status) {
        this.unitId = unitId; this.enterpriseId = enterpriseId; this.parentId = parentId; this.unitType = unitType; this.name = name; this.code = code; this.leaderMemberId = leaderMemberId; this.description = description; this.sortOrder = sortOrder; this.status = status;
    }
    public String unitId() { return unitId; }
    public String enterpriseId() { return enterpriseId; }
    public String parentId() { return parentId; }
    public String unitType() { return unitType; }
    public String name() { return name; }
    public String code() { return code; }
    public String leaderMemberId() { return leaderMemberId; }
    public String description() { return description; }
    public int sortOrder() { return sortOrder; }
    public String status() { return status; }
}
