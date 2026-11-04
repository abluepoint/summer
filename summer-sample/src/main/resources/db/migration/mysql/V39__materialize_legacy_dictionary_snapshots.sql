-- DDL is a checkpoint, not a transaction rollback boundary. Preserve pre-upgrade bytes/rows.
CREATE TABLE summer_dictionary_v39_type_backup AS SELECT * FROM summer_dictionary_type;
CREATE TABLE summer_dictionary_v39_item_backup AS SELECT * FROM summer_dictionary_item;
CREATE TABLE summer_dictionary_v39_version_backup AS SELECT * FROM summer_dictionary_version;
CREATE TABLE summer_dictionary_v39_override_backup AS
    SELECT * FROM summer_configuration_override WHERE resource = 'dictionaries';

CREATE TABLE summer_dictionary_upgrade_issue (
    tenant_id VARCHAR(80) NOT NULL,
    dictionary_code VARCHAR(80) NOT NULL,
    issue_type VARCHAR(80) NOT NULL,
    reference_id VARCHAR(80) NOT NULL
) ENGINE=InnoDB;

INSERT INTO summer_dictionary_upgrade_issue
SELECT t.tenant_id, t.code, 'MISSING_SELECTED_SNAPSHOT', t.id
FROM summer_dictionary_type t
WHERE t.published_version IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM summer_dictionary_version v WHERE v.tenant_id=t.tenant_id
     AND v.dictionary_code=t.code AND v.version_number=t.published_version);
INSERT INTO summer_dictionary_upgrade_issue
SELECT t.tenant_id, t.code, 'HISTORY_WITHOUT_POINTER', t.id
FROM summer_dictionary_type t WHERE t.published_version IS NULL AND EXISTS
    (SELECT 1 FROM summer_dictionary_version v WHERE v.tenant_id=t.tenant_id AND v.dictionary_code=t.code);

-- Old CRUD flags used to close/private an existing snapshot immediately. Preserve that denial
-- in the explicit publication control; never reactivate it by switching to snapshot-only reads.
INSERT INTO summer_dictionary_upgrade_issue
SELECT t.tenant_id,t.code,'LEGACY_CONTROL_DIFFERS_FROM_SNAPSHOT',t.id
FROM summer_dictionary_type t JOIN summer_dictionary_version v
ON v.tenant_id=t.tenant_id AND v.dictionary_code=t.code AND v.version_number=t.published_version
WHERE t.published=true AND ((t.enabled=false AND JSON_EXTRACT(v.snapshot_json,'$.enabled')=true)
OR (t.public_readable=false AND JSON_EXTRACT(v.snapshot_json,'$.publicReadable')=true));
UPDATE summer_dictionary_type t JOIN summer_dictionary_upgrade_issue i
ON i.tenant_id=t.tenant_id AND i.dictionary_code=t.code AND i.issue_type='LEGACY_CONTROL_DIFFERS_FROM_SNAPSHOT'
SET t.published=false;

INSERT INTO summer_dictionary_version
(version_id, tenant_id, dictionary_code, version_number, status, snapshot_json, created_by, request_id, created_at)
SELECT UUID(), t.tenant_id, t.code, 1, 'PUBLISHED',
    JSON_OBJECT('name', t.name, 'publicReadable', IF(t.public_readable, CAST('true' AS JSON), CAST('false' AS JSON)),
    'enabled', IF(t.enabled, CAST('true' AS JSON), CAST('false' AS JSON)),
    'items', CAST(COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('value', i.item_value, 'label', i.label,
        'sortOrder', i.sort_order, 'enabled', IF(i.enabled, CAST('true' AS JSON), CAST('false' AS JSON))))
        OVER (ORDER BY i.sort_order, i.item_value ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
        FROM summer_dictionary_item i WHERE i.tenant_id=t.tenant_id AND i.dictionary_code=t.code LIMIT 1), JSON_ARRAY()) AS JSON)),
    'dictionary-v39-upgrade', NULL, CURRENT_TIMESTAMP(6)
FROM summer_dictionary_type t
WHERE t.published=true AND t.enabled=true AND t.published_version IS NULL
AND NOT EXISTS (SELECT 1 FROM summer_dictionary_version v WHERE v.tenant_id=t.tenant_id AND v.dictionary_code=t.code);

UPDATE summer_dictionary_type t JOIN summer_dictionary_version v
ON v.tenant_id=t.tenant_id AND v.dictionary_code=t.code AND v.created_by='dictionary-v39-upgrade'
SET t.published_version=v.version_number WHERE t.published_version IS NULL AND t.published=true AND t.enabled=true;

-- Keep invalid references intact and visible to operators; runtime never falls back to editing rows.
INSERT INTO summer_dictionary_upgrade_issue
SELECT o.tenant_id, o.config_key, 'OVERRIDE_WITHOUT_BASE', o.id
FROM summer_configuration_override o WHERE o.resource='dictionaries' AND NOT EXISTS
(SELECT 1 FROM summer_dictionary_type t WHERE t.tenant_id=o.tenant_id AND t.code=o.config_key);
INSERT INTO summer_dictionary_upgrade_issue
SELECT DISTINCT o.tenant_id, o.config_key, 'OVERRIDE_ITEM_OUTSIDE_SNAPSHOT', o.id
FROM summer_configuration_override o
JOIN summer_dictionary_type t ON t.tenant_id=o.tenant_id AND t.code=o.config_key
LEFT JOIN summer_dictionary_version v ON v.tenant_id=t.tenant_id AND v.dictionary_code=t.code AND v.version_number=t.published_version
JOIN JSON_TABLE(o.value_json, '$[*]' COLUMNS (item_value VARCHAR(80) PATH '$.value')) opt
WHERE o.resource='dictionaries' AND o.published_revision IS NOT NULL
AND (v.version_id IS NULL OR NOT JSON_CONTAINS(v.snapshot_json, JSON_OBJECT('value', opt.item_value), '$.items'));
