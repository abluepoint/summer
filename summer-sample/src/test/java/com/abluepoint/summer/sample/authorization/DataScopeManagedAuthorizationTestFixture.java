package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.api.permission.TrustedManagedTestFixture;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Explicit in-memory test installation and persisted grants; never a product startup repair. */
final class DataScopeManagedAuthorizationTestFixture {
    static final String TENANT = "ent-acme";
    static final String DOMAIN = "ENTERPRISE:" + TENANT;
    private static final String GROUP = "fixture08-data";
    private static final List<String> SCOPES = List.of(
            "enterprise.roles.read", "enterprise.roles.write",
            "enterprise.data-scope.read", "enterprise.data-scope.write",
            "system.parameter.read", "system.parameter.write",
            "platform.dictionary.read", "platform.dictionary.write");
    private final JdbcTemplate db;

    @TestConfiguration(proxyBeanMethods = false)
    @Import(TrustedManagedTestFixture.class)
    static class Schema {
        @Bean
        ApplicationRunner dataScopeManagedSchema(JdbcTemplate db) {
            return args -> {
                for (String name : List.of(
                        "V60__permission_management.sql",
                        "V70__role_permission_and_resource_associations.sql",
                        "V80__permission_resource_controls.sql",
                        "V81__managed_interface_lifecycle.sql",
                        "V92__support_full_length_menu_resource_ids.sql",
                        "V100__permission_data_details.sql",
                        "V101__application_read_resource.sql",
                        "V110__permission_selected_departments.sql")) {
                    String sql = new String(new ClassPathResource("db/migration/mysql/" + name)
                            .getContentAsByteArray(), StandardCharsets.UTF_8);
                    if (name.startsWith("V60")) sql = sql.substring(0, sql.indexOf("ALTER TABLE"));
                    for (String statement : sql.replaceAll("(?m)--[^\\n]*", "").split(";")) {
                        if (!statement.isBlank()) db.execute(statement.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
                    }
                }
                db.update("insert into summer_menu_publication(publication_id,published,manifest_version) values('global',false,0) on duplicate key update publication_id=publication_id");
            };
        }
    }

    DataScopeManagedAuthorizationTestFixture(JdbcTemplate db, ManagedInterfaceResources interfaces) {
        this.db = db;
        if (!"MANAGED".equals(db.queryForObject(
                "select engine_mode from summer_authorization_domain where domain_key=?", String.class, DOMAIN))) {
            throw new IllegalStateException("Explicit fresh managed test installation required");
        }
        interfaces.scan();
        db.update("insert into summer_permission_group(domain_key,group_id,name,sort_order,version) values(?,?,'DATA regression fixture',0,0)", DOMAIN, GROUP);
    }

    void identity(String subject) {
        db.update("insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at) select ?,'!unusable-fixture-login','USER',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_auth_user where username=?)", subject, subject);
        db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) select ?,?,?,?,?,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_enterprise_member where enterprise_id=? and subject=?)",
                TENANT, id("member:" + subject), subject, subject, "fixture08@example.test", TENANT, subject);
        db.update("insert into summer_permission_user(domain_key,username,version) select ?,?,0 where not exists(select 1 from summer_permission_user where domain_key=? and username=?)", DOMAIN, subject, DOMAIN, subject);
        role(subject, roleId(subject));
    }

    void role(String subject, String role) {
        db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at) select ?,?,'DATA fixture',?,false,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_authorization_role where role_id=?)", role, TENANT, role, role);
        db.update("insert into summer_permission_role(domain_key,role_id) select ?,? where not exists(select 1 from summer_permission_role where domain_key=? and role_id=?)", DOMAIN, role, DOMAIN, role);
        db.update("insert into summer_permission_user_role(domain_key,username,role_id) select ?,?,? where not exists(select 1 from summer_permission_user_role where domain_key=? and username=? and role_id=?)", DOMAIN, subject, role, DOMAIN, subject, role);
    }

    /** Only the configuration and DATA diagnostic HTTP families used by these tests. */
    void interfaces(String subject) {
        identity(subject);
        int matched = 0;
        for (var row : db.queryForList("select resource_id,normalized_path from summer_interface_resource where active=true and configurable=true and domain_kind='ENTERPRISE'")) {
            String path = row.get("normalized_path").toString();
            if (!(path.equals("/api/system-parameters") || path.startsWith("/api/system-parameters/")
                    || path.equals("/api/dictionaries") || path.startsWith("/api/dictionaries/")
                    || path.startsWith("/api/v1/enterprises/{enterpriseId}/data-resources/"))) continue;
            String resource = row.get("resource_id").toString();
            // Reuse any installed association's access mode; configuration cannot mix modes.
            var modes = db.queryForList("select distinct p.access_mode from summer_permission_resource x join summer_permission p on p.domain_key=x.domain_key and p.permission_id=x.permission_id where x.domain_key=? and x.resource_type='INTERFACE' and x.resource_id=?", String.class, DOMAIN, resource);
            if (modes.size() > 1) throw new IllegalStateException("Mixed interface modes in test installation");
            String permission = permission("INTERFACE", "interface:" + resource, modes.isEmpty() ? "AUTHORIZED" : modes.getFirst());
            db.update("insert into summer_permission_resource(domain_key,permission_id,resource_type,resource_id) select ?,?,'INTERFACE',? where not exists(select 1 from summer_permission_resource where domain_key=? and permission_id=? and resource_id=?)", DOMAIN, permission, resource, DOMAIN, permission, resource);
            grant(roleId(subject), permission);
            matched++;
        }
        if (matched == 0) throw new IllegalStateException("Installed test interfaces required");
        advance();
    }

    String data(String subject, String resource, String action, String preset, String... departments) {
        identity(subject);
        return dataForRole(roleId(subject), resource, action, preset, departments);
    }

    String dataForRole(String role, String resource, String action, String preset, String... departments) {
        String permission = permission("DATA", UUID.randomUUID().toString(), "AUTHORIZED");
        db.update("insert into summer_permission_data_scope(domain_key,permission_id,data_resource_key,action,scope_preset) values(?,?,?,?,?)", DOMAIN, permission, resource, action, preset);
        for (String department : departments) db.update("insert into summer_permission_selected_department(domain_key,permission_id,department_id) values(?,?,?)", DOMAIN, permission, department);
        grant(role, permission);
        advance();
        return permission;
    }

    void revoke(String subject, String resource, String action) {
        db.update("delete from summer_role_permission where domain_key=? and role_id in(select role_id from summer_permission_user_role where domain_key=? and username=?) and permission_id in(select permission_id from summer_permission_data_scope where domain_key=? and data_resource_key=? and action=?)", DOMAIN, DOMAIN, subject, DOMAIN, resource, action);
        advance();
    }

    void revoke(String role, String permission) {
        db.update("delete from summer_role_permission where domain_key=? and role_id=? and permission_id=?", DOMAIN, role, permission);
        advance();
    }

    void assignments(String subject, String... organizations) {
        String member = db.queryForObject("select member_id from summer_enterprise_member where enterprise_id=? and subject=?", String.class, TENANT, subject);
        db.update("delete from summer_organization_membership where enterprise_id=? and member_id=?", TENANT, member);
        for (String organization : organizations) db.update("insert into summer_organization_membership(enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values(?,?,?,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", TENANT, member, organization);
        advance();
    }

    void advance() {
        if (db.update("update summer_authorization_domain set revision=revision+1 where domain_key=? and engine_mode='MANAGED'", DOMAIN) != 1) throw new IllegalStateException("Managed fixture domain disappeared");
    }

    static RequestPostProcessor member(String subject) {
        return jwt().jwt(j -> j.subject(subject).claim("scope", SCOPES)
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", TENANT, "status", "ACTIVE"))))
                .authorities(SCOPES.stream().map(s -> new SimpleGrantedAuthority("SCOPE_" + s)).toArray(SimpleGrantedAuthority[]::new));
    }

    static JwtAuthenticationToken principal(String subject) {
        var token = Jwt.withTokenValue("fixture08").header("alg", "none").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return new JwtAuthenticationToken(token, List.of());
    }

    static String roleId(String subject) { return id("role:" + subject); }

    private String permission(String type, String key, String mode) {
        String permission = id(type + ":" + key);
        db.update("insert into summer_permission_identity(domain_key,permission_id,code,type) select ?,?,?,? where not exists(select 1 from summer_permission_identity where domain_key=? and permission_id=?)", DOMAIN, permission, permission, type, DOMAIN, permission);
        db.update("insert into summer_permission(domain_key,permission_id,code,name,type,access_mode,group_id,status,version) select ?,?,?,'DATA fixture',?,?,?,'ACTIVE',0 where not exists(select 1 from summer_permission where domain_key=? and permission_id=?)", DOMAIN, permission, permission, type, mode, GROUP, DOMAIN, permission);
        return permission;
    }

    private void grant(String role, String permission) {
        db.update("insert into summer_role_permission(domain_key,role_id,permission_id) select ?,?,? where not exists(select 1 from summer_role_permission where domain_key=? and role_id=? and permission_id=?)", DOMAIN, role, permission, DOMAIN, role, permission);
    }

    private static String id(String value) {
        return "fixture08-" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
