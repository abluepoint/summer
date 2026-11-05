-- MENU identities have always allowed 160 characters; INTERFACE ids remain limited by their resolver.
ALTER TABLE summer_permission_resource MODIFY COLUMN resource_id VARCHAR(160) NOT NULL;
ALTER TABLE summer_permission_resource_control MODIFY COLUMN target_id VARCHAR(160) NOT NULL;
