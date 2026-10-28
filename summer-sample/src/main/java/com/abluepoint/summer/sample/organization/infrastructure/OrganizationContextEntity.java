package com.abluepoint.summer.sample.organization.infrastructure;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_organization_context", uniqueConstraints = @UniqueConstraint(name = "uk_summer_org_context_subject", columnNames = "subject"))
public class OrganizationContextEntity extends BaseLongEntity {
    @Column(nullable = false, length = 128) public String subject;
    @Column(nullable = false, length = 64) public String enterpriseId;
    @Column(length = 64) public String organizationId;
    @Column(nullable = false, length = 128) public String revision;
    protected OrganizationContextEntity() {}
    public OrganizationContextEntity(String subject, String enterpriseId, String organizationId, String revision) { this.subject = subject; this.enterpriseId = enterpriseId; this.organizationId = organizationId; this.revision = revision; }
}
