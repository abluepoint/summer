-- Compatibility expansion only. No domain is switched to the new policy model.
CREATE TABLE summer_authorization_domain (
    domain_key VARCHAR(96) NOT NULL PRIMARY KEY,
    engine_mode VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    CONSTRAINT ck_authorization_revision CHECK (revision >= 0)
);
CREATE TABLE summer_authorization_resource_identity (
    resource_id VARCHAR(96) NOT NULL PRIMARY KEY,
    locator_hash VARCHAR(64) NOT NULL UNIQUE,
    legacy_code VARCHAR(200) NOT NULL
);
ALTER TABLE summer_authorization_audit
    ADD COLUMN authorization_resource_id VARCHAR(96) NULL,
    ADD COLUMN authorization_revision BIGINT NULL;
