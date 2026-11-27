-- Current assignments begin empty. The retired V200 membership history stays inactive.
CREATE TABLE summer_platform_group_administrator (
    group_id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    PRIMARY KEY (group_id, username),
    FOREIGN KEY (group_id) REFERENCES summer_platform_user_group(id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES summer_platform_administrator(username) ON DELETE CASCADE
);
