-- Non-destructive preparation: managed role writers no longer name legacy projection columns.
-- The separately approved contract command drops them only after all managed gates pass.
ALTER TABLE summer_authorization_role MODIFY COLUMN capability_ids TEXT NOT NULL DEFAULT (CONCAT('',''));
ALTER TABLE summer_authorization_role MODIFY COLUMN capability_codes TEXT NOT NULL DEFAULT (CONCAT('',''));
CREATE TABLE summer_authorization_contract (
 operation_id VARCHAR(128) NOT NULL PRIMARY KEY,
 singleton_key TINYINT NOT NULL DEFAULT 1 UNIQUE,
 approval_sha256 VARCHAR(64) NOT NULL,
 backup_manifest_sha256 VARCHAR(64) NOT NULL,
 state_fingerprint VARCHAR(64) NOT NULL,
 target_artifact_sha256 VARCHAR(64) NOT NULL,
 runtime_account VARCHAR(32) NOT NULL,
 runtime_host VARCHAR(255) NOT NULL,
 phase VARCHAR(16) NOT NULL,
 checkpoint_json LONGTEXT NOT NULL,
 actor VARCHAR(128) NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_authorization_contract_phase CHECK(phase IN ('PREPARED','COMPLETED')),
 CONSTRAINT ck_authorization_contract_singleton CHECK(singleton_key=1)
);
