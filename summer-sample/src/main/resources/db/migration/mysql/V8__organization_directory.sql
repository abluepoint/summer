CREATE TABLE summer_organization_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    unit_id VARCHAR(64) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    unit_type VARCHAR(32) NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(64) NOT NULL,
    leader_member_id VARCHAR(64),
    description VARCHAR(500),
    sort_order INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_organization_unit PRIMARY KEY (id),
    CONSTRAINT uk_summer_org_unit_id UNIQUE (unit_id)
);
