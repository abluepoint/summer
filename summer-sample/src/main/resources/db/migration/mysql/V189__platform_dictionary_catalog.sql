-- Reject duplicate dictionary codes at the scope boundary.
ALTER TABLE summer_dictionary_type ADD CONSTRAINT uq_dictionary_global_code UNIQUE (code);
INSERT INTO summer_dictionary_type (id,tenant_id,code,name,published,public_readable,enabled,published_version,draft_revision,effective_revision)
VALUES (UUID(),'platform','interface-service-name','服务名称',true,true,true,1,1,1);
ALTER TABLE summer_dictionary_item DROP FOREIGN KEY fk_summer_dictionary_tenant_type,
    ADD CONSTRAINT fk_dictionary_global_item_type FOREIGN KEY (dictionary_code) REFERENCES summer_dictionary_type(code),
    ADD UNIQUE (dictionary_code,id);
ALTER TABLE summer_dictionary_group DROP FOREIGN KEY fk_dictionary_group_type,
    ADD CONSTRAINT fk_dictionary_global_group_type FOREIGN KEY (dictionary_code) REFERENCES summer_dictionary_type(code),
    ADD UNIQUE (dictionary_code,id);
ALTER TABLE summer_dictionary_group_member DROP FOREIGN KEY fk_dictionary_member_group,
    DROP FOREIGN KEY fk_dictionary_member_item,
    ADD CONSTRAINT fk_dictionary_global_member_group FOREIGN KEY (dictionary_code,group_id)
        REFERENCES summer_dictionary_group(dictionary_code,id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_dictionary_global_member_item FOREIGN KEY (dictionary_code,item_id)
        REFERENCES summer_dictionary_item(dictionary_code,id) ON DELETE CASCADE;

UPDATE summer_dictionary_type SET tenant_id='platform', organization_id=NULL;
UPDATE summer_dictionary_item SET tenant_id='platform', organization_id=NULL;
UPDATE summer_dictionary_group SET tenant_id='platform';
UPDATE summer_dictionary_group_member SET tenant_id='platform';
UPDATE summer_dictionary_version SET tenant_id='platform';
UPDATE summer_dictionary_notification_event SET tenant_id='platform';
UPDATE summer_dictionary_outbox SET tenant_id='platform';

ALTER TABLE summer_interface_service_catalog ADD COLUMN service_code VARCHAR(80);
UPDATE summer_interface_service_catalog SET service_code=CONCAT('svc-',SHA2(service_name,256));
INSERT INTO summer_dictionary_item (id,tenant_id,dictionary_code,item_value,label,sort_order,enabled,description,ever_published)
SELECT UUID(),'platform','interface-service-name',service_code,service_name,ROW_NUMBER() OVER (ORDER BY service_name),true,'',true
FROM summer_interface_service_catalog;
INSERT INTO summer_dictionary_version (version_id,tenant_id,dictionary_code,version_number,status,snapshot_json,created_by,created_at)
SELECT UUID(),'platform','interface-service-name',1,'PUBLISHED',
    JSON_OBJECT('name','服务名称','enabled',CAST('true' AS JSON),'publicReadable',CAST('true' AS JSON),'formatVersion',2,'groups',JSON_ARRAY(),
        'items',CAST(COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('value',item_value,'label',label,'sortOrder',sort_order,
        'description','','enabled',CAST('true' AS JSON))) OVER (ORDER BY sort_order ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
        FROM summer_dictionary_item WHERE dictionary_code='interface-service-name' LIMIT 1),'[]') AS JSON)),
    'migration-v189',CURRENT_TIMESTAMP(6);
ALTER TABLE summer_interface_service_catalog DROP PRIMARY KEY,
    MODIFY service_code VARCHAR(80) NOT NULL PRIMARY KEY,
    MODIFY service_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
