CREATE TABLE IF NOT EXISTS summer_data_scope_policy (
    policy_id VARCHAR(96) NOT NULL PRIMARY KEY,
    enterprise_id VARCHAR(64) NOT NULL,
    resource VARCHAR(160) NOT NULL,
    operator_name VARCHAR(8) NOT NULL,
    rules_json TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_summer_data_scope_policy UNIQUE (enterprise_id, resource),
    CONSTRAINT ck_summer_data_scope_policy_operator CHECK (operator_name IN ('AND','OR'))
);
ALTER TABLE summer_system_parameter ADD COLUMN organization_id VARCHAR(64) NULL;
ALTER TABLE summer_system_parameter ADD COLUMN created_by VARCHAR(128) NULL;
ALTER TABLE summer_system_parameter ADD COLUMN owner_id VARCHAR(128) NULL;
CREATE INDEX ix_summer_system_parameter_scope ON summer_system_parameter (tenant_id, organization_id, created_by, owner_id);
ALTER TABLE summer_dictionary_type ADD COLUMN tenant_id VARCHAR(80) NOT NULL DEFAULT 'ent-acme';
ALTER TABLE summer_dictionary_type ADD COLUMN organization_id VARCHAR(64) NULL;
ALTER TABLE summer_dictionary_type ADD COLUMN created_by VARCHAR(128) NULL;
ALTER TABLE summer_dictionary_type ADD COLUMN owner_id VARCHAR(128) NULL;
ALTER TABLE summer_dictionary_item ADD COLUMN tenant_id VARCHAR(80) NOT NULL DEFAULT 'ent-acme';
ALTER TABLE summer_dictionary_item ADD COLUMN organization_id VARCHAR(64) NULL;
ALTER TABLE summer_dictionary_item ADD COLUMN created_by VARCHAR(128) NULL;
ALTER TABLE summer_dictionary_item ADD COLUMN owner_id VARCHAR(128) NULL;
CREATE INDEX ix_summer_dictionary_type_scope ON summer_dictionary_type (tenant_id, organization_id, created_by, owner_id);
