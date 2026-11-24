ALTER TABLE summer_permission_data_scope
 MODIFY data_resource_key VARCHAR(96) NULL,
 MODIFY action VARCHAR(64) NULL;

CREATE TABLE summer_permission_data_interface (
 domain_key VARCHAR(96) NOT NULL,
 permission_id VARCHAR(96) NOT NULL,
 interface_permission_id VARCHAR(96) NOT NULL,
 PRIMARY KEY(domain_key,permission_id,interface_permission_id),
 FOREIGN KEY(domain_key,permission_id)
   REFERENCES summer_permission_data_scope(domain_key,permission_id) ON DELETE CASCADE,
 FOREIGN KEY(domain_key,interface_permission_id)
   REFERENCES summer_permission(domain_key,permission_id)
);
