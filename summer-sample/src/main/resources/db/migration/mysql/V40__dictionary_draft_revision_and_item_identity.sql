-- DDL checkpoints are deliberately retained for operator recovery.
CREATE TABLE summer_dictionary_v40_type_backup AS SELECT * FROM summer_dictionary_type;
CREATE TABLE summer_dictionary_v40_item_backup AS SELECT * FROM summer_dictionary_item;
ALTER TABLE summer_dictionary_type ADD COLUMN draft_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE summer_dictionary_item ADD COLUMN description VARCHAR(2000) NOT NULL DEFAULT '', ADD COLUMN ever_published BOOLEAN NOT NULL DEFAULT FALSE;
-- Every immutable history entry contributes; the selected rollback pointer is insufficient.
UPDATE summer_dictionary_item i SET ever_published = TRUE WHERE EXISTS (
 SELECT 1 FROM summer_dictionary_version v,
 JSON_TABLE(v.snapshot_json, '$.items[*]' COLUMNS(item_value VARCHAR(80) PATH '$.value')) h
 WHERE v.tenant_id = i.tenant_id AND v.dictionary_code = i.dictionary_code AND BINARY h.item_value = BINARY i.item_value
);

ALTER TABLE summer_dictionary_version MODIFY COLUMN snapshot_json LONGTEXT NOT NULL;
