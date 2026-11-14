-- An operation receipt, never an alternative source for authorization mode or grants.
CREATE TABLE summer_authorization_installation (
 operation_id VARCHAR(128) NOT NULL PRIMARY KEY,
 domain_key VARCHAR(96) NOT NULL UNIQUE,
 actor VARCHAR(128) NOT NULL,
 provenance_hash VARCHAR(64) NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 completed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
