CREATE TABLE IF NOT EXISTS summer_security_key_rotation (
    rotation_id TINYINT NOT NULL PRIMARY KEY,
    period_days INT NOT NULL,
    retention_days INT NOT NULL,
    next_rotation_at TIMESTAMP(6) NOT NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    last_success_at TIMESTAMP(6) NULL,
    status VARCHAR(32) NOT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(240) NULL,
    attempted_key_version INT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
