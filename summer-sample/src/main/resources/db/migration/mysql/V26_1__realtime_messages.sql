CREATE TABLE summer_realtime_message (
    message_id VARCHAR(36) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    sender_principal VARCHAR(128) NOT NULL,
    client_message_id VARCHAR(128) NOT NULL,
    recipient_principal VARCHAR(128) NULL,
    payload_json LONGTEXT NOT NULL,
    sequence_no BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    acknowledged_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_realtime_message_client (enterprise_id, sender_principal, client_message_id),
    KEY ix_realtime_message_cursor (enterprise_id, sequence_no)
);
