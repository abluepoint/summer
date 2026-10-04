ALTER TABLE summer_data_scope_policy
    ADD COLUMN IF NOT EXISTS conditions_json TEXT NOT NULL DEFAULT 'null',
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS summer_data_scope_policy_audit (
    audit_id VARCHAR(96) NOT NULL PRIMARY KEY,
    enterprise_id VARCHAR(64) NOT NULL,
    resource VARCHAR(160) NOT NULL,
    version BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    principal VARCHAR(128) NULL,
    request_id VARCHAR(96) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    INDEX ix_summer_data_scope_audit_lookup (enterprise_id, resource, occurred_at)
);
