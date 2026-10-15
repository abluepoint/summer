-- Preserve existing tenant attribution and rows; add composite keys before dropping global keys.
ALTER TABLE summer_system_parameter
    ADD CONSTRAINT uq_summer_parameter_tenant_key UNIQUE (tenant_id, parameter_key),
    DROP INDEX uq_summer_system_parameter_key;
ALTER TABLE summer_system_parameter_version
    ADD CONSTRAINT uq_summer_parameter_tenant_idempotency UNIQUE (tenant_id, parameter_key, idempotency_key),
    DROP INDEX uk_summer_parameter_version_idempotency;
ALTER TABLE summer_dictionary_type
    ADD CONSTRAINT uq_summer_dictionary_tenant_code UNIQUE (tenant_id, code);
ALTER TABLE summer_dictionary_item
    ADD CONSTRAINT uq_summer_dictionary_tenant_value UNIQUE (tenant_id, dictionary_code, item_value),
    ADD CONSTRAINT fk_summer_dictionary_tenant_type FOREIGN KEY (tenant_id, dictionary_code)
        REFERENCES summer_dictionary_type (tenant_id, code),
    DROP FOREIGN KEY fk_summer_dictionary_item_type;
ALTER TABLE summer_dictionary_type DROP INDEX uk_summer_dictionary_type_code;
ALTER TABLE summer_dictionary_item DROP INDEX uk_summer_dictionary_item_value;
