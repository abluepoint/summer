ALTER TABLE summer_authorization_capability DROP INDEX uk_summer_authorization_capability_code;
CREATE UNIQUE INDEX uk_summer_authorization_capability_owner_code
    ON summer_authorization_capability (scope, (COALESCE(enterprise_id, '')), code);
