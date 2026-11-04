CREATE TABLE summer_permission_group (
 domain_key VARCHAR(96) NOT NULL, group_id VARCHAR(96) NOT NULL, parent_group_id VARCHAR(96) NULL,
 name VARCHAR(200) NOT NULL, sort_order INT NOT NULL, version BIGINT NOT NULL,
 PRIMARY KEY(domain_key,group_id),
 CONSTRAINT fk_permission_group_parent FOREIGN KEY(domain_key,parent_group_id) REFERENCES summer_permission_group(domain_key,group_id)
);
CREATE TABLE summer_permission_identity (
 domain_key VARCHAR(96) NOT NULL, permission_id VARCHAR(96) NOT NULL, code VARCHAR(128) NOT NULL, type VARCHAR(16) NOT NULL,
 PRIMARY KEY(domain_key,permission_id), UNIQUE(domain_key,code), UNIQUE(domain_key,permission_id,code,type)
);
CREATE TABLE summer_permission (
 domain_key VARCHAR(96) NOT NULL, permission_id VARCHAR(96) NOT NULL, code VARCHAR(128) NOT NULL,
 name VARCHAR(200) NOT NULL, description VARCHAR(2000) NULL, type VARCHAR(16) NOT NULL,
 access_mode VARCHAR(20) NOT NULL, group_id VARCHAR(96) NOT NULL, status VARCHAR(16) NOT NULL,
 version BIGINT NOT NULL,
 PRIMARY KEY(domain_key,permission_id), UNIQUE(domain_key,code),
 CONSTRAINT fk_permission_identity FOREIGN KEY(domain_key,permission_id,code,type) REFERENCES summer_permission_identity(domain_key,permission_id,code,type),
 CONSTRAINT fk_permission_group FOREIGN KEY(domain_key,group_id) REFERENCES summer_permission_group(domain_key,group_id),
 CONSTRAINT ck_permission_type CHECK(type IN ('INTERFACE','MENU','DATA')),
 CONSTRAINT ck_permission_access CHECK(access_mode IN ('AUTHORIZED','AUTHENTICATED','PUBLIC')),
 CONSTRAINT ck_permission_status CHECK(status IN ('ACTIVE','DISABLED'))
);
CREATE TABLE summer_permission_mutation (
 domain_key VARCHAR(96) NOT NULL, actor VARCHAR(128) NOT NULL, operation_key VARCHAR(200) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL, fingerprint VARCHAR(64) NOT NULL,
 response_json MEDIUMTEXT NOT NULL, response_status INT NOT NULL, response_version BIGINT NOT NULL,
 PRIMARY KEY(domain_key,actor,operation_key,idempotency_key)
);

ALTER TABLE summer_authorization_audit MODIFY enterprise_id VARCHAR(96) NOT NULL;
ALTER TABLE summer_authorization_change_outbox MODIFY enterprise_id VARCHAR(96) NOT NULL;
