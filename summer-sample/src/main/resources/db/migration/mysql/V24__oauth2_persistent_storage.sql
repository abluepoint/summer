CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    client_secret VARCHAR(200) NULL,
    client_secret_expires_at TIMESTAMP(6) NULL,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods TEXT NOT NULL,
    authorization_grant_types TEXT NOT NULL,
    redirect_uris TEXT NULL,
    post_logout_redirect_uris TEXT NULL,
    scopes TEXT NOT NULL,
    client_settings TEXT NOT NULL,
    token_settings TEXT NOT NULL,
    CONSTRAINT pk_oauth2_registered_client PRIMARY KEY (id),
    CONSTRAINT uk_oauth2_registered_client_client_id UNIQUE (client_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes TEXT NULL,
    attributes TEXT NULL,
    state VARCHAR(500) NULL,
    authorization_code_value TEXT NULL,
    authorization_code_issued_at TIMESTAMP(6) NULL,
    authorization_code_expires_at TIMESTAMP(6) NULL,
    authorization_code_metadata TEXT NULL,
    access_token_value TEXT NULL,
    access_token_issued_at TIMESTAMP(6) NULL,
    access_token_expires_at TIMESTAMP(6) NULL,
    access_token_metadata TEXT NULL,
    access_token_type VARCHAR(100) NULL,
    access_token_scopes TEXT NULL,
    oidc_id_token_value TEXT NULL,
    oidc_id_token_issued_at TIMESTAMP(6) NULL,
    oidc_id_token_expires_at TIMESTAMP(6) NULL,
    oidc_id_token_metadata TEXT NULL,
    oidc_id_token_claims TEXT NULL,
    refresh_token_value TEXT NULL,
    refresh_token_issued_at TIMESTAMP(6) NULL,
    refresh_token_expires_at TIMESTAMP(6) NULL,
    refresh_token_metadata TEXT NULL,
    user_code_value TEXT NULL,
    user_code_issued_at TIMESTAMP(6) NULL,
    user_code_expires_at TIMESTAMP(6) NULL,
    user_code_metadata TEXT NULL,
    device_code_value TEXT NULL,
    device_code_issued_at TIMESTAMP(6) NULL,
    device_code_expires_at TIMESTAMP(6) NULL,
    device_code_metadata TEXT NULL,
    CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id),
    INDEX ix_oauth2_authorization_state (state),
    INDEX ix_oauth2_authorization_code (authorization_code_value(255)),
    INDEX ix_oauth2_authorization_access_token (access_token_value(255)),
    INDEX ix_oauth2_authorization_refresh_token (refresh_token_value(255)),
    INDEX ix_oauth2_authorization_user_code (user_code_value(255)),
    INDEX ix_oauth2_authorization_device_code (device_code_value(255))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities TEXT NOT NULL,
    CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS summer_oauth_client_status (
    client_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_summer_oauth_client_status PRIMARY KEY (client_id),
    CONSTRAINT ck_summer_oauth_client_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;
