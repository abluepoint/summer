ALTER TABLE summer_authorization_role
    ADD COLUMN capability_ids TEXT NOT NULL,
    ADD COLUMN capability_codes TEXT NOT NULL,
    ADD COLUMN bundle_id VARCHAR(96) NULL;
