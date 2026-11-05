ALTER TABLE summer_application_record ADD COLUMN note VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE summer_application_record ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;
