ALTER TABLE summer_configuration_override
    ADD COLUMN latest_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN published_revision BIGINT NULL;
CREATE TABLE summer_configuration_override_revision (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    override_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(80) NOT NULL,
    revision BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    value_json TEXT NOT NULL,
    public_readable BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    principal VARCHAR(128) NOT NULL,
    request_id VARCHAR(120),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_configuration_override_revision UNIQUE (override_id, revision),
    CONSTRAINT fk_configuration_override_revision FOREIGN KEY (override_id) REFERENCES summer_configuration_override (id),
    CONSTRAINT ck_configuration_override_revision_state CHECK (state IN ('DRAFT','PUBLISHED','DISABLED','ROLLED_BACK'))
) ENGINE=InnoDB;
-- V30 writes were immediately effective. Preserve that state as revision 1 without exposing values in audit.
INSERT INTO summer_configuration_override_revision
    (id,override_id,tenant_id,revision,state,value_json,public_readable,enabled,principal,created_at)
SELECT UUID(),id,tenant_id,1,CASE WHEN enabled THEN 'PUBLISHED' ELSE 'DISABLED' END,
    value_json,public_readable,enabled,created_by,updated_at FROM summer_configuration_override;
UPDATE summer_configuration_override SET latest_revision=1,published_revision=1;
