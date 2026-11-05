CREATE TABLE summer_application_record (
 record_id VARCHAR(96) NOT NULL PRIMARY KEY,
 domain_key VARCHAR(96) NOT NULL,
 tenant_id VARCHAR(80) NOT NULL,
 applicant_id VARCHAR(128) NOT NULL,
 created_by VARCHAR(128) NOT NULL,
 department_id VARCHAR(96),
 business_visible BOOLEAN NOT NULL,
 INDEX ix_application_tenant_applicant(tenant_id,applicant_id)
);
