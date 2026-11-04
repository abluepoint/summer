-- No old rows or historical snapshots are rewritten. Existing dictionaries start without groups.
ALTER TABLE summer_dictionary_item ADD CONSTRAINT uk_dictionary_item_identity UNIQUE (tenant_id, dictionary_code, id);
CREATE TABLE summer_dictionary_group (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(80) NOT NULL,
 dictionary_code VARCHAR(80) NOT NULL,
 code VARCHAR(80) NOT NULL,
 name VARCHAR(200) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 ever_published BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT uk_dictionary_group_code UNIQUE (tenant_id, dictionary_code, code),
 CONSTRAINT uk_dictionary_group_identity UNIQUE (tenant_id, dictionary_code, id),
 CONSTRAINT fk_dictionary_group_type FOREIGN KEY (tenant_id, dictionary_code) REFERENCES summer_dictionary_type (tenant_id, code)
) ENGINE=InnoDB;
CREATE TABLE summer_dictionary_group_member (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(80) NOT NULL,
 dictionary_code VARCHAR(80) NOT NULL,
 group_id VARCHAR(36) NOT NULL,
 item_id VARCHAR(36) NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT uk_dictionary_group_member UNIQUE (tenant_id, dictionary_code, group_id, item_id),
 CONSTRAINT fk_dictionary_member_group FOREIGN KEY (tenant_id, dictionary_code, group_id) REFERENCES summer_dictionary_group (tenant_id, dictionary_code, id) ON DELETE CASCADE,
 CONSTRAINT fk_dictionary_member_item FOREIGN KEY (tenant_id, dictionary_code, item_id) REFERENCES summer_dictionary_item (tenant_id, dictionary_code, id) ON DELETE CASCADE
) ENGINE=InnoDB;
