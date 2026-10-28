package com.abluepoint.summer.sample.organization.infrastructure;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_organization_membership", uniqueConstraints = @UniqueConstraint(name = "uk_summer_org_membership", columnNames = {"enterprise_id", "member_id", "organization_id"}))
public class OrganizationMembershipEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64) public String enterpriseId;
    @Column(nullable = false, length = 64) public String memberId;
    @Column(nullable = false, length = 64) public String organizationId;
    @Column(nullable = false) public boolean primaryOrganization;
    protected OrganizationMembershipEntity() {}
    public OrganizationMembershipEntity(String enterpriseId, String memberId, String organizationId, boolean primaryOrganization) { this.enterpriseId = enterpriseId; this.memberId = memberId; this.organizationId = organizationId; this.primaryOrganization = primaryOrganization; }
    public String enterpriseId() { return enterpriseId; }
    public String memberId() { return memberId; }
    public String organizationId() { return organizationId; }
    public boolean primaryOrganization() { return primaryOrganization; }
}
