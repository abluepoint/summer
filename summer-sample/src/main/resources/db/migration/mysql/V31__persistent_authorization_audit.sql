ALTER TABLE summer_authorization_audit
    ADD COLUMN resource_id VARCHAR(256) NOT NULL DEFAULT '',
    ADD COLUMN action VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN principal VARCHAR(128) NOT NULL DEFAULT '';

UPDATE summer_authorization_audit
SET resource_id = CONCAT('user:', username),
    action = CASE WHEN decision = 'ALLOW' THEN 'AUTHORIZATION_ALLOWED' ELSE 'AUTHORIZATION_DENIED' END,
    principal = username;

CREATE INDEX ix_summer_authorization_audit_resource
    ON summer_authorization_audit (enterprise_id, resource_id, audit_id);
