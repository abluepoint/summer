package com.abluepoint.summer.sample.authorization.infrastructure.data;

import jakarta.persistence.*;

@Entity
@Table(name = "summer_application_record")
public class ApplicationRecordEntity {
    @Id
    @Column(name = "record_id", length = 96)
    private String recordId;

    @Column(name = "domain_key", nullable = false, length = 96)
    private String domainKey;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(name = "applicant_id", nullable = false, length = 128)
    private String applicantId;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "department_id", length = 96)
    private String departmentId;

    @Column(name = "business_visible", nullable = false)
    private boolean businessVisible;

    public String getRecordId() {
        return recordId;
    }

    public String getApplicantId() {
        return applicantId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    protected ApplicationRecordEntity() {}
}
