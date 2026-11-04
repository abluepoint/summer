-- Existing role identities remain shared; new grants are isolated by authorization domain.
CREATE TABLE summer_permission_role (
 domain_key VARCHAR(96) NOT NULL, role_id VARCHAR(96) NOT NULL,
 PRIMARY KEY(domain_key,role_id),
 FOREIGN KEY(role_id) REFERENCES summer_authorization_role(role_id)
);
CREATE TABLE summer_role_permission (
 domain_key VARCHAR(96) NOT NULL, role_id VARCHAR(96) NOT NULL, permission_id VARCHAR(96) NOT NULL,
 PRIMARY KEY(domain_key,role_id,permission_id),
 FOREIGN KEY(domain_key,role_id) REFERENCES summer_permission_role(domain_key,role_id),
 FOREIGN KEY(domain_key,permission_id) REFERENCES summer_permission(domain_key,permission_id)
);
CREATE TABLE summer_permission_user (
 domain_key VARCHAR(96) NOT NULL, username VARCHAR(128) NOT NULL, version BIGINT NOT NULL,
 PRIMARY KEY(domain_key,username)
);
CREATE TABLE summer_permission_user_role (
 domain_key VARCHAR(96) NOT NULL, username VARCHAR(128) NOT NULL, role_id VARCHAR(96) NOT NULL,
 PRIMARY KEY(domain_key,username,role_id),
 FOREIGN KEY(domain_key,username) REFERENCES summer_permission_user(domain_key,username),
 FOREIGN KEY(domain_key,role_id) REFERENCES summer_permission_role(domain_key,role_id)
);
-- A single relationship source for INTERFACE and MENU, serialized by the domain row lock.
CREATE TABLE summer_permission_resource (
 domain_key VARCHAR(96) NOT NULL, permission_id VARCHAR(96) NOT NULL,
 resource_type VARCHAR(16) NOT NULL, resource_id VARCHAR(96) NOT NULL,
 PRIMARY KEY(domain_key,permission_id,resource_type,resource_id),
 FOREIGN KEY(domain_key,permission_id) REFERENCES summer_permission(domain_key,permission_id),
 CHECK(resource_type IN ('INTERFACE','MENU'))
);
CREATE TABLE summer_permission_bootstrap (
 domain_key VARCHAR(96) NOT NULL PRIMARY KEY, initialized_at TIMESTAMP(6) NOT NULL
);
