CREATE TABLE summer_permission_data_scope (
 domain_key VARCHAR(96) NOT NULL,
 permission_id VARCHAR(96) NOT NULL,
 data_resource_key VARCHAR(96) NOT NULL,
 action VARCHAR(64) NOT NULL,
 scope_preset VARCHAR(32) NOT NULL,
 PRIMARY KEY(domain_key,permission_id),
 CONSTRAINT fk_permission_data_detail FOREIGN KEY(domain_key,permission_id)
 REFERENCES summer_permission(domain_key,permission_id) ON DELETE CASCADE
);
