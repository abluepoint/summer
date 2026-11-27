CREATE TABLE summer_file_repository (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    storage_type VARCHAR(8) NOT NULL,
    location VARCHAR(128) NOT NULL,
    UNIQUE (storage_type, location)
);
CREATE TABLE summer_file_repository_install (id INT PRIMARY KEY);
