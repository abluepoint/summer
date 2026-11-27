CREATE TABLE summer_file_entry_creation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    directory_key VARBINARY(1160) NOT NULL,
    entry_name VARBINARY(1024) NOT NULL,
    is_folder BOOLEAN NOT NULL,
    created_by VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_file_entry_creation (directory_key, entry_name, is_folder)
);
