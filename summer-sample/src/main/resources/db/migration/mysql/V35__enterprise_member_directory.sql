CREATE TABLE summer_enterprise_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_summer_member_tenant_id UNIQUE (enterprise_id, member_id),
    CONSTRAINT uk_summer_member_tenant_subject UNIQUE (enterprise_id, subject)
);
-- Persist the old demonstration assignment once; subsequent empty assignments stay empty after restart.
INSERT INTO summer_organization_membership (enterprise_id, member_id, organization_id, primary_organization, created_at, updated_at)
SELECT 'ent-acme', 'member-alice', 'org-engineering', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE EXISTS (SELECT 1 FROM summer_organization_unit WHERE unit_id = 'org-engineering' AND enterprise_id = 'ent-acme')
AND NOT EXISTS (SELECT 1 FROM summer_organization_membership WHERE enterprise_id = 'ent-acme' AND member_id = 'member-alice');

CREATE TABLE summer_directory_journal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id VARCHAR(64) NOT NULL,
    namespace VARCHAR(64) NOT NULL,
    operation_key VARCHAR(255) NOT NULL,
    resource_id VARCHAR(128),
    fingerprint TEXT,
    payload LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_summer_directory_operation UNIQUE (enterprise_id, namespace, operation_key)
);
ALTER TABLE summer_organization_unit ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
