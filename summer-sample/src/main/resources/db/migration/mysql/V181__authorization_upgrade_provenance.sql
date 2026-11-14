-- Operator provenance only. Runtime authorization continues to use the unique domain mode row.
CREATE TABLE summer_authorization_bridge_enrollment (
 domain_key VARCHAR(96) NOT NULL PRIMARY KEY,
 operation_id VARCHAR(128) NOT NULL,
 actor VARCHAR(128) NOT NULL,
 bridge_sha256 VARCHAR(64) NOT NULL,
 schema_fingerprint VARCHAR(64) NOT NULL,
 approval_sha256 VARCHAR(64) NOT NULL,
 enrolled_revision BIGINT NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
