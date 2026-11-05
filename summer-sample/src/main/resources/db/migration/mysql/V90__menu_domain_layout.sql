CREATE TABLE summer_menu_domain_layout (
    layout_id VARCHAR(320) NOT NULL PRIMARY KEY,
    domain_key VARCHAR(100) NOT NULL,
    menu_id VARCHAR(160) NOT NULL,
    enabled BOOLEAN NULL,
    parent_menu_id VARCHAR(160) NULL,
    sort_order INT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_menu_domain_layout UNIQUE (domain_key, menu_id),
    CONSTRAINT ck_menu_domain_layout_order CHECK (sort_order IS NULL OR sort_order >= 0)
);

CREATE TABLE summer_menu_publication (
    publication_id VARCHAR(32) NOT NULL PRIMARY KEY,
    published BOOLEAN NOT NULL,
    manifest_version BIGINT NOT NULL
);
INSERT INTO summer_menu_publication(publication_id,published,manifest_version)
SELECT 'global', (COUNT(*) > 0 OR EXISTS (SELECT 1 FROM summer_authorization_audit WHERE action='MENU_MANIFEST_SYNCED')),
       COALESCE(MAX(manifest_version),0)
FROM summer_menu_manifest_item;
