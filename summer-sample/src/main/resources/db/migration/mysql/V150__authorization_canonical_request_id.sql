-- Preserve the full canonical request ID accepted by RequestCorrelationFilter.
ALTER TABLE summer_authorization_audit MODIFY request_id VARCHAR(128) NULL;
ALTER TABLE summer_authorization_change_outbox MODIFY request_id VARCHAR(128) NULL;
