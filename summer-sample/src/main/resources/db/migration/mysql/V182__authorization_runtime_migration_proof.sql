-- Derived proof, not runtime authority. Original v160/v170 evidence remains immutable.
CREATE TABLE summer_authorization_runtime_migration_proof (
 snapshot_id VARCHAR(96) NOT NULL PRIMARY KEY,
 source_fingerprint VARCHAR(64) NOT NULL,
 policy_version VARCHAR(96) NOT NULL,
 proof_hash VARCHAR(64) NOT NULL,
 proof_json LONGTEXT NOT NULL,
 actor VARCHAR(128) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(snapshot_id) REFERENCES summer_authorization_migration_snapshot(snapshot_id)
);
