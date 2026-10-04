CREATE TABLE summer_dictionary_notification_event (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(80) NOT NULL,
    dictionary_code VARCHAR(80) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    version_number BIGINT NOT NULL,
    request_id VARCHAR(120) NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_dictionary_notification_event PRIMARY KEY (event_id)
) ENGINE=InnoDB;
CREATE INDEX ix_summer_dictionary_notification_lookup
    ON summer_dictionary_notification_event (tenant_id, dictionary_code, event_id);
