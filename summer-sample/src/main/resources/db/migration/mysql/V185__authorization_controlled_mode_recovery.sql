-- Recovery restores only an absent mode row from independently authenticated current facts.
CREATE TABLE summer_authorization_mode_recovery (
 operation_id VARCHAR(128) NOT NULL PRIMARY KEY,
 domain_key VARCHAR(96) NOT NULL,
 approval_sha256 VARCHAR(64) NOT NULL,
 backup_manifest_sha256 VARCHAR(64) NOT NULL,
 recovery_fingerprint VARCHAR(64) NOT NULL,
 target_artifact_sha256 VARCHAR(64) NOT NULL,
 backup_revision BIGINT NOT NULL,
 committed_revision BIGINT NOT NULL,
 actor VARCHAR(128) NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 completed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_authorization_mode_recovery_revision CHECK(backup_revision >= 0 AND committed_revision > backup_revision)
);
