-- Older publishers deleted item rows for an empty manifest. The atomic outbox
-- retains its version even when the menu list and/or audit retention are empty.
UPDATE summer_menu_publication
SET published = published OR EXISTS (
        SELECT 1 FROM summer_authorization_change_outbox
        WHERE resource_type='MENU_MANIFEST' AND resource_id='manifest'
          AND event_type='MENU_MANIFEST_SYNCED'
    ),
    manifest_version = GREATEST(manifest_version, COALESCE((
        SELECT MAX(resource_version) FROM summer_authorization_change_outbox
        WHERE resource_type='MENU_MANIFEST' AND resource_id='manifest'
          AND event_type='MENU_MANIFEST_SYNCED'
    ), 0))
WHERE publication_id='global';
