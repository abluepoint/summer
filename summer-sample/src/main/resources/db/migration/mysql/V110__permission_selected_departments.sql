CREATE TABLE summer_permission_selected_department (
 domain_key VARCHAR(96) NOT NULL,
 permission_id VARCHAR(96) NOT NULL,
 department_id VARCHAR(64) NOT NULL,
 PRIMARY KEY(domain_key,permission_id,department_id),
 CONSTRAINT fk_permission_selected_department FOREIGN KEY(domain_key,permission_id)
 REFERENCES summer_permission_data_scope(domain_key,permission_id) ON DELETE CASCADE
);
