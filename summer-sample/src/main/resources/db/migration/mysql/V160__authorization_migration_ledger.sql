-- Expand only: immutable source evidence and resumable staged target facts. No engine switch.
CREATE TABLE summer_authorization_migration_snapshot (
 snapshot_id VARCHAR(96) NOT NULL PRIMARY KEY,
 domain_key VARCHAR(96) NOT NULL,
 source_revision BIGINT NOT NULL,
 expected_revision BIGINT NOT NULL,
 source_fingerprint VARCHAR(64) NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 evidence_json LONGTEXT NOT NULL,
 plan_json LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(domain_key,source_fingerprint),
 CONSTRAINT ck_migration_status CHECK(status IN ('BLOCKED','PLANNED','STAGING','STAGED','DATA_PENDING'))
);
CREATE TABLE summer_authorization_migration_step (
 snapshot_id VARCHAR(96) NOT NULL,
 step_no INT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 target_id VARCHAR(160) NOT NULL,
 applied BOOLEAN NOT NULL DEFAULT FALSE,
 applied_revision BIGINT NULL,
 PRIMARY KEY(snapshot_id,step_no),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_migration_snapshot(snapshot_id)
);
CREATE TABLE summer_authorization_migration_mapping (
 snapshot_id VARCHAR(96) NOT NULL,
 ordinal INT NOT NULL,
 source_kind VARCHAR(32) NOT NULL,
 source_id VARCHAR(400) NOT NULL,
 target_kind VARCHAR(32) NOT NULL,
 target_id VARCHAR(160) NOT NULL,
 PRIMARY KEY(snapshot_id,ordinal),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_migration_snapshot(snapshot_id)
);
