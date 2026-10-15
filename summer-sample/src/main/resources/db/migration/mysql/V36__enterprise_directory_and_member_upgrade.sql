ALTER TABLE summer_enterprise ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE summer_enterprise ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- One-time compatibility migration: existing active local users with persisted tenant role
-- bindings retain their membership as an explicit row. New role bindings do not imply membership.
INSERT INTO summer_enterprise_member (enterprise_id, member_id, subject, display_name, email, status, version, created_at, updated_at)
SELECT b.enterprise_id, CASE WHEN b.enterprise_id='ent-acme' AND b.username IN ('alice','bob') THEN CONCAT('member-', b.username) ELSE CONCAT('member-migrated-', UUID()) END,
       b.username, b.username, '', 'ACTIVE', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM summer_authorization_user_binding b
JOIN summer_auth_user u ON u.username = b.username AND u.active = TRUE
JOIN summer_enterprise e ON e.enterprise_id = b.enterprise_id AND e.status = 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM summer_enterprise_member m WHERE m.enterprise_id = b.enterprise_id AND m.subject = b.username);
