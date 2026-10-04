ALTER TABLE summer_dictionary_type
    ADD COLUMN published_version BIGINT NULL;

CREATE TABLE summer_dictionary_version (
    version_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(80) NOT NULL,
    dictionary_code VARCHAR(80) NOT NULL,
    version_number BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_by VARCHAR(128) NULL,
    request_id VARCHAR(120) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_dictionary_version PRIMARY KEY (version_id),
    CONSTRAINT uk_summer_dictionary_version_number UNIQUE (tenant_id, dictionary_code, version_number),
    CONSTRAINT ck_summer_dictionary_version_status CHECK (status IN ('PUBLISHED'))
) ENGINE=InnoDB;
CREATE INDEX ix_summer_dictionary_version_lookup
    ON summer_dictionary_version (tenant_id, dictionary_code, version_number);

CREATE TABLE summer_dictionary_outbox (
    event_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(80) NOT NULL,
    dictionary_code VARCHAR(80) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    version_number BIGINT NOT NULL,
    request_id VARCHAR(120) NULL,
    payload_json TEXT NOT NULL,
    idempotency_key VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_dictionary_outbox PRIMARY KEY (event_id),
    CONSTRAINT ck_summer_dictionary_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
) ENGINE=InnoDB;
CREATE UNIQUE INDEX uk_summer_dictionary_outbox_idempotency
    ON summer_dictionary_outbox (tenant_id, dictionary_code, idempotency_key);
CREATE INDEX ix_summer_dictionary_outbox_lookup
    ON summer_dictionary_outbox (tenant_id, dictionary_code, created_at);
