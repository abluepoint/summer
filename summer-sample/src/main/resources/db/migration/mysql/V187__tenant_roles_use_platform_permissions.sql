-- New tenant grants reference the platform catalog. Historical domain-local grants stay intact.
CREATE TABLE summer_role_platform_permission (
 domain_key VARCHAR(96) NOT NULL,
 role_id VARCHAR(96) NOT NULL,
 permission_id VARCHAR(96) NOT NULL,
 catalog_domain_key VARCHAR(96) NOT NULL DEFAULT 'PLATFORM:platform',
 PRIMARY KEY(domain_key,role_id,permission_id),
 CONSTRAINT fk_catalog_grant_role FOREIGN KEY(domain_key,role_id)
   REFERENCES summer_permission_role(domain_key,role_id),
 CONSTRAINT fk_catalog_grant_permission FOREIGN KEY(catalog_domain_key,permission_id)
   REFERENCES summer_permission(domain_key,permission_id),
 CONSTRAINT ck_catalog_grant_platform CHECK(catalog_domain_key='PLATFORM:platform'),
 CONSTRAINT ck_catalog_grant_tenant CHECK(domain_key LIKE 'ENTERPRISE:%')
);
