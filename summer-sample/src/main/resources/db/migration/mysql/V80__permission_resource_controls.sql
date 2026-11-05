-- Relationship versions are independent from permission definitions and catalog scans.
CREATE TABLE summer_permission_resource_control (
 domain_key VARCHAR(96) NOT NULL,
 resource_type VARCHAR(16) NOT NULL,
 control_kind VARCHAR(16) NOT NULL,
 target_id VARCHAR(96) NOT NULL,
 version BIGINT NOT NULL,
 PRIMARY KEY(domain_key,resource_type,control_kind,target_id),
 CHECK(resource_type IN ('INTERFACE','MENU')),
 CHECK(control_kind IN ('COLLECTION','RESOURCE')),
 CHECK(version >= 0)
);
