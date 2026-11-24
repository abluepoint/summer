CREATE TABLE summer_administrative_division (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(9) UNIQUE,
    name VARCHAR(120) NOT NULL,
    area_level SMALLINT NOT NULL,
    parent_id VARCHAR(36),
    source_date DATE,
    revision BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_division_parent FOREIGN KEY (parent_id) REFERENCES summer_administrative_division(id),
    INDEX ix_division_children (parent_id, code)
);
