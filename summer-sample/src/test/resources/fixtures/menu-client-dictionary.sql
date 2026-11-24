INSERT INTO summer_dictionary_type (id,tenant_id,code,name,published,public_readable,enabled,published_version,draft_revision,effective_revision)
VALUES (UUID(),'platform','menu-client','菜单所属客户端',true,true,true,1,1,1);
INSERT INTO summer_dictionary_item (id,tenant_id,dictionary_code,item_value,label,sort_order,enabled,description,ever_published)
VALUES (UUID(),'platform','menu-client','web','Web 管理端',0,true,'',true);
INSERT INTO summer_dictionary_version (version_id,tenant_id,dictionary_code,version_number,status,snapshot_json,created_by,created_at)
VALUES (UUID(),'platform','menu-client',1,'PUBLISHED',
    '{"name":"菜单所属客户端","enabled":true,"publicReadable":true,"formatVersion":2,"groups":[],"items":[{"value":"web","label":"Web 管理端","sortOrder":0,"description":"","enabled":true}]}',
    'migration-v190',CURRENT_TIMESTAMP);
