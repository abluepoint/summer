CREATE TABLE IF NOT EXISTS summer_system_parameter_version (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(80) NOT NULL,
    parameter_key VARCHAR(120) NOT NULL,
    revision BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    snapshot_json TEXT NOT NULL,
    base_version BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    principal VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_summer_system_parameter_version PRIMARY KEY (id),
    CONSTRAINT uk_summer_system_parameter_revision UNIQUE (tenant_id, parameter_key, revision),
    CONSTRAINT uk_summer_parameter_version_idempotency UNIQUE (parameter_key, idempotency_key),
    CONSTRAINT ck_summer_system_parameter_version_state CHECK (state IN ('DRAFT', 'PUBLISHED', 'ROLLED_BACK', 'INACTIVE'))
) ENGINE = InnoDB;
CREATE INDEX ix_summer_system_parameter_version_key_time
    ON summer_system_parameter_version (tenant_id, parameter_key, created_at);
