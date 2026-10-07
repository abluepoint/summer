CREATE TABLE summer_configuration_override (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(80) NOT NULL,
 resource VARCHAR(160) NOT NULL,
 config_key VARCHAR(160) NOT NULL,
 scope_id VARCHAR(64) NOT NULL,
 organization_id VARCHAR(64),
 created_by VARCHAR(128) NOT NULL,
 owner_id VARCHAR(128) NOT NULL,
 value_json TEXT NOT NULL,
 public_readable BOOLEAN NOT NULL,
 enabled BOOLEAN NOT NULL,
 version BIGINT NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_configuration_override UNIQUE (tenant_id,resource,config_key,scope_id)
);
