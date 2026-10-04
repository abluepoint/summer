CREATE TABLE summer_menu_manifest_item (
    menu_id VARCHAR(160) NOT NULL,
    parent_menu_id VARCHAR(160) NULL,
    path VARCHAR(500) NOT NULL,
    title_key VARCHAR(240) NOT NULL,
    icon_key VARCHAR(120) NOT NULL,
    sort_order INT NOT NULL,
    capability_code VARCHAR(200) NULL,
    manifest_version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_menu_manifest_item PRIMARY KEY (menu_id),
    CONSTRAINT ck_summer_menu_manifest_sort CHECK (sort_order >= 0)
);
CREATE INDEX ix_summer_menu_manifest_parent_order ON summer_menu_manifest_item (parent_menu_id, sort_order, menu_id);

CREATE TABLE summer_menu_manifest_override (
    override_id VARCHAR(240) NOT NULL,
    enterprise_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(160) NOT NULL,
    enabled BOOLEAN NULL,
    parent_menu_id VARCHAR(160) NULL,
    sort_order INT NULL,
    capability_code VARCHAR(200) NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_summer_menu_manifest_override PRIMARY KEY (override_id),
    CONSTRAINT uk_summer_menu_manifest_override UNIQUE (enterprise_id, menu_id),
    CONSTRAINT ck_summer_menu_manifest_override_sort CHECK (sort_order IS NULL OR sort_order >= 0)
);
