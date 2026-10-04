CREATE TABLE summer_enterprise (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_enterprise PRIMARY KEY (id),
    CONSTRAINT uk_summer_enterprise_code UNIQUE (code)
);
