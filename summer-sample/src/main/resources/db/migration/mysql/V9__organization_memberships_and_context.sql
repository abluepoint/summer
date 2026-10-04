CREATE TABLE summer_organization_membership (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    organization_id VARCHAR(64) NOT NULL,
    primary_organization BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_organization_membership PRIMARY KEY (id),
    CONSTRAINT uk_summer_org_membership UNIQUE (enterprise_id, member_id, organization_id)
);

CREATE TABLE summer_organization_context (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subject VARCHAR(128) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    organization_id VARCHAR(64),
    revision VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_organization_context PRIMARY KEY (id),
    CONSTRAINT uk_summer_org_context_subject UNIQUE (subject)
);
