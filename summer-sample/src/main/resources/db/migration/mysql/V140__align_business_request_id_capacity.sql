-- RequestCorrelationFilter accepts up to 128 characters. Keep business evidence lossless.
ALTER TABLE summer_audit_event MODIFY COLUMN request_id VARCHAR(128) NULL;
ALTER TABLE summer_system_parameter_outbox MODIFY COLUMN request_id VARCHAR(128) NULL;
ALTER TABLE summer_dictionary_version MODIFY COLUMN request_id VARCHAR(128) NULL;
ALTER TABLE summer_dictionary_outbox MODIFY COLUMN request_id VARCHAR(128) NULL;
ALTER TABLE summer_dictionary_notification_event MODIFY COLUMN request_id VARCHAR(128) NULL;
ALTER TABLE summer_configuration_override_revision MODIFY COLUMN request_id VARCHAR(128) NULL;
