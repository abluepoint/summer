-- Derived DATA evidence, never a second authorization identity ledger.
CREATE TABLE summer_authorization_data_migration (
 snapshot_id VARCHAR(96) NOT NULL PRIMARY KEY,
 source_fingerprint VARCHAR(64) NOT NULL,
 plan_hash VARCHAR(64) NOT NULL,
 proof_version VARCHAR(64) NOT NULL,
 registry_fingerprint VARCHAR(64) NOT NULL,
 derived_plan_json LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 verification_json LONGTEXT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_migration_snapshot(snapshot_id),
 CONSTRAINT ck_data_migration_status CHECK(status IN ('BLOCKED','PLANNED','APPLIED','VERIFIED'))
);
CREATE TABLE summer_authorization_data_migration_step (
 snapshot_id VARCHAR(96) NOT NULL,
 step_no INT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 target_id VARCHAR(160) NOT NULL,
 applied BOOLEAN NOT NULL DEFAULT FALSE,
 applied_revision BIGINT NULL,
 PRIMARY KEY(snapshot_id,step_no),
 UNIQUE(snapshot_id,kind,target_id),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_data_migration(snapshot_id),
 CONSTRAINT ck_data_migration_step CHECK(kind IN ('DATA_PERMISSION','DATA_ROLE','DATA_USER'))
);
