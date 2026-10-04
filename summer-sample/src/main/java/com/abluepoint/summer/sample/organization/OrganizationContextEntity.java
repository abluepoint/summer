package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_organization_context", uniqueConstraints = @UniqueConstraint(name = "uk_summer_org_context_subject", columnNames = "subject"))
class OrganizationContextEntity extends BaseLongEntity {
    @Column(nullable = false, length = 128) String subject;
    @Column(nullable = false, length = 64) String enterpriseId;
    @Column(length = 64) String organizationId;
    @Column(nullable = false, length = 128) String revision;
    protected OrganizationContextEntity() {}
    OrganizationContextEntity(String subject, String enterpriseId, String organizationId, String revision) { this.subject = subject; this.enterpriseId = enterpriseId; this.organizationId = organizationId; this.revision = revision; }
}
