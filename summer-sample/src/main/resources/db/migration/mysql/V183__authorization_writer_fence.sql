-- Checkpoints for nontransactional account/connection isolation. Never used for runtime ALLOW.
CREATE TABLE summer_authorization_writer_fence (
 fence_id VARCHAR(128) NOT NULL PRIMARY KEY,
 snapshot_id VARCHAR(96) NOT NULL,
 domain_key VARCHAR(96) NOT NULL,
 source_fingerprint VARCHAR(64) NOT NULL,
 source_schema VARCHAR(64) NOT NULL,
 old_account VARCHAR(32) NOT NULL,
 old_host VARCHAR(255) NOT NULL,
 approval_sha256 VARCHAR(64) NOT NULL,
 execution_receipt_sha256 VARCHAR(64) NOT NULL,
 grants_hash VARCHAR(64) NULL,
 phase VARCHAR(16) NOT NULL,
 checkpoint_json LONGTEXT NOT NULL,
 actor VARCHAR(128) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_migration_snapshot(snapshot_id),
 UNIQUE(snapshot_id,old_account,old_host,source_schema),
 CONSTRAINT ck_authorization_fence_phase CHECK(phase IN ('FENCING','FENCED'))
);
