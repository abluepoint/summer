CREATE TABLE summer_authorization_capability (
    capability_id VARCHAR(96) NOT NULL,
    enterprise_id VARCHAR(64) NULL,
    scope VARCHAR(32) NOT NULL,
    code VARCHAR(200) NOT NULL,
    resource VARCHAR(200) NOT NULL,
    action VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_capability PRIMARY KEY (capability_id),
    CONSTRAINT uk_summer_authorization_capability_code UNIQUE (code)
);

CREATE TABLE summer_authorization_role (
    role_id VARCHAR(96) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(128) NOT NULL,
    built_in BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_role PRIMARY KEY (role_id),
    CONSTRAINT uk_summer_authorization_role_code UNIQUE (enterprise_id, code)
);

CREATE TABLE summer_authorization_role_capability (
    role_id VARCHAR(96) NOT NULL,
    capability_id VARCHAR(96) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_role_capability PRIMARY KEY (role_id, capability_id),
    CONSTRAINT fk_summer_authorization_role_capability_role FOREIGN KEY (role_id) REFERENCES summer_authorization_role (role_id),
    CONSTRAINT fk_summer_authorization_role_capability_capability FOREIGN KEY (capability_id) REFERENCES summer_authorization_capability (capability_id)
);

CREATE TABLE summer_authorization_user_binding (
    binding_id VARCHAR(96) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    role_ids JSON NOT NULL,
    denied_capability_codes JSON NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_user_binding PRIMARY KEY (binding_id),
    CONSTRAINT uk_summer_authorization_user_binding_user UNIQUE (enterprise_id, username)
);

CREATE TABLE summer_authorization_resource_policy (
    resource_id VARCHAR(96) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    normalized_path VARCHAR(500) NOT NULL,
    permission_code VARCHAR(200) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    public_access BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_resource_policy PRIMARY KEY (resource_id),
    CONSTRAINT uk_summer_authorization_resource_policy_path UNIQUE (http_method, normalized_path)
);

CREATE TABLE summer_authorization_audit (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    permission_code VARCHAR(200) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    request_id VARCHAR(120) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_authorization_audit PRIMARY KEY (audit_id),
    INDEX ix_summer_authorization_audit_subject (enterprise_id, username, occurred_at)
);
