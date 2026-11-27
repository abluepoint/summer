CREATE TABLE summer_platform_administrator (
    username VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    FOREIGN KEY (username) REFERENCES summer_auth_user(username)
);
INSERT INTO summer_platform_administrator(username,display_name)
SELECT DISTINCT u.username,u.username FROM summer_permission_user_role r
JOIN summer_auth_user u ON u.username=r.username WHERE r.domain_key='PLATFORM:platform';

-- The fixed root is virtual. A null parent denotes a first-level department.
CREATE TABLE summer_platform_department (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    FOREIGN KEY (parent_id) REFERENCES summer_platform_department(id)
);
CREATE TABLE summer_platform_membership (
    username VARCHAR(128) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    primary_department BOOLEAN NOT NULL,
    leader BOOLEAN NOT NULL,
    primary_owner VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN primary_department THEN username END) STORED,
    led_department VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN leader THEN department_id END) STORED,
    PRIMARY KEY (username,department_id),
    UNIQUE (primary_owner),
    UNIQUE (led_department),
    FOREIGN KEY (username) REFERENCES summer_platform_administrator(username),
    FOREIGN KEY (department_id) REFERENCES summer_platform_department(id)
);
CREATE TABLE summer_platform_directory_install (
    id INT PRIMARY KEY
);
