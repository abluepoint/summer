CREATE TABLE IF NOT EXISTS summer_authorization_change_outbox (
    event_id VARCHAR(36) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    resource_version BIGINT NOT NULL,
    request_id VARCHAR(120) NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_change_outbox PRIMARY KEY (event_id),
    CONSTRAINT ck_summer_authorization_change_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD_LETTER')),
    CONSTRAINT ck_summer_authorization_change_outbox_attempts CHECK (attempts >= 0),
    INDEX ix_summer_authorization_change_outbox_due (status, next_attempt_at, created_at),
    INDEX ix_summer_authorization_change_outbox_resource (enterprise_id, resource_type, resource_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS summer_authorization_change_receipt (
    event_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_change_receipt PRIMARY KEY (event_id),
    CONSTRAINT ck_summer_authorization_change_receipt_status CHECK (status IN ('PROCESSED', 'RETRY', 'DEAD_LETTER'))
) ENGINE = InnoDB;
