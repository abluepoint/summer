-- Local operator receipts. These are never consulted to grant runtime access.
CREATE TABLE summer_authorization_cutover (
 operation_id VARCHAR(128) NOT NULL PRIMARY KEY,
 snapshot_id VARCHAR(96) NOT NULL,
 domain_key VARCHAR(96) NOT NULL,
 purpose VARCHAR(32) NOT NULL,
 approval_sha256 VARCHAR(64) NOT NULL,
 source_fingerprint VARCHAR(64) NOT NULL,
 authorization_fingerprint VARCHAR(64) NOT NULL,
 target_artifact_sha256 VARCHAR(64) NOT NULL,
 from_revision BIGINT NOT NULL,
 committed_revision BIGINT NOT NULL,
 evidence_json LONGTEXT NOT NULL,
 actor VARCHAR(128) NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 completed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(domain_key),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_migration_snapshot(snapshot_id),
 CONSTRAINT ck_authorization_cutover_purpose CHECK(purpose IN ('ISOLATED_COMPARISON','PRODUCTION_CUTOVER')),
 CONSTRAINT ck_authorization_cutover_revision CHECK(from_revision >= 0 AND committed_revision > from_revision)
);
