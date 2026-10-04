package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_organization_membership", uniqueConstraints = @UniqueConstraint(name = "uk_summer_org_membership", columnNames = {"enterprise_id", "member_id", "organization_id"}))
class OrganizationMembershipEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64) String enterpriseId;
    @Column(nullable = false, length = 64) String memberId;
    @Column(nullable = false, length = 64) String organizationId;
    @Column(nullable = false) boolean primaryOrganization;
    protected OrganizationMembershipEntity() {}
    OrganizationMembershipEntity(String enterpriseId, String memberId, String organizationId, boolean primaryOrganization) { this.enterpriseId = enterpriseId; this.memberId = memberId; this.organizationId = organizationId; this.primaryOrganization = primaryOrganization; }
    String enterpriseId() { return enterpriseId; }
    String memberId() { return memberId; }
    String organizationId() { return organizationId; }
    boolean primaryOrganization() { return primaryOrganization; }
}
