CREATE TABLE summer_platform_user_group (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE
);
CREATE TABLE summer_platform_group_member (
    group_id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    PRIMARY KEY (group_id, username),
    FOREIGN KEY (group_id) REFERENCES summer_platform_user_group(id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES summer_platform_administrator(username) ON DELETE CASCADE
);
CREATE TABLE summer_platform_group_role (
    group_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(96) NOT NULL,
    PRIMARY KEY (group_id, role_id),
    FOREIGN KEY (group_id) REFERENCES summer_platform_user_group(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES summer_authorization_role(role_id) ON DELETE CASCADE
);
