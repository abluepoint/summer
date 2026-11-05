CREATE TABLE summer_interface_catalog_control (
    control_id INT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL
);
INSERT INTO summer_interface_catalog_control VALUES (1, 0);

-- Each incarnation is immutable; disappearance and replacement retire its identity.
CREATE TABLE summer_interface_resource (
    resource_id VARCHAR(96) NOT NULL PRIMARY KEY,
    locator_hash VARCHAR(64) NOT NULL,
    generation BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    handler_hash VARCHAR(64) NOT NULL,
    application_id VARCHAR(96) NOT NULL,
    source VARCHAR(96) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    normalized_path VARCHAR(1024) NOT NULL,
    conditions_json TEXT NOT NULL,
    handler_provenance VARCHAR(2048) NOT NULL,
    domain_kind VARCHAR(16) NOT NULL,
    configurable BOOLEAN NOT NULL,
    UNIQUE (locator_hash, generation)
);
