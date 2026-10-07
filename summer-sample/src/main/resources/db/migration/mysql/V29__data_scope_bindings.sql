CREATE TABLE summer_data_scope_binding (
 binding_id VARCHAR(96) NOT NULL PRIMARY KEY,
 enterprise_id VARCHAR(64) NOT NULL,
 resource VARCHAR(160) NOT NULL,
 subject_type VARCHAR(8) NOT NULL,
 subject_id VARCHAR(128) NOT NULL,
 operator_name VARCHAR(8) NOT NULL,
 rules_json TEXT NOT NULL,
 conditions_json TEXT NOT NULL,
 checksum VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_data_scope_binding UNIQUE (enterprise_id, resource, subject_type, subject_id)
);
