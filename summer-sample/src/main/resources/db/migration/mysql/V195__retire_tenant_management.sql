-- Platform layouts and role grants share tables with other domains.
DELETE FROM summer_menu_domain_layout WHERE domain_key LIKE 'ENTERPRISE:%';

-- Remove bindings to retired MVC resources, including previous resource generations.
-- Keep the scanner's historical identities and snapshots for audit.
DELETE b FROM summer_permission_resource b
JOIN summer_interface_resource i ON b.resource_type='INTERFACE' AND b.resource_id=i.resource_id
WHERE i.application_id='application' AND i.source='MVC'
AND (
  i.normalized_path REGEXP '^/api/v1/enterprises/[^/]+/(permission-groups|permissions|interface-resources|dictionaries)(/|$)'
  OR i.normalized_path REGEXP '^/api/v1/enterprises/[^/]+/authorization/(interface-directory|menu-directory|menu-layout|menu-manifest)$'
  OR i.normalized_path REGEXP '^/api/v1/enterprises/[^/]+/menu-manifest/'
  OR (i.normalized_path='/api/v1/enterprises/{enterpriseId}/menu-manifest' AND i.http_method='PATCH')
);
UPDATE summer_authorization_domain SET revision=revision+1;
DROP TABLE summer_menu_manifest_override;
