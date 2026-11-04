ALTER TABLE summer_dictionary_type ADD COLUMN effective_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE summer_dictionary_outbox ADD COLUMN request_fingerprint VARCHAR(64) NULL,
    ADD COLUMN result_json LONGTEXT NULL;
ALTER TABLE summer_dictionary_version MODIFY COLUMN snapshot_json LONGTEXT NOT NULL;
