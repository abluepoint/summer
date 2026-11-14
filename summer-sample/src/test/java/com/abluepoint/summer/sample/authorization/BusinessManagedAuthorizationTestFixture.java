package com.abluepoint.summer.sample.authorization;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;

/** Explicit trusted test installation; never an application startup hook or an old grant adapter. */
public final class BusinessManagedAuthorizationTestFixture implements AutoCloseable {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final String namespace = java.util.UUID.randomUUID().toString();
    private final Set<String> domains = new java.util.HashSet<>();
    private final Set<String> createdDomains = new java.util.HashSet<>();
    private final Set<String> createdUsers = new java.util.HashSet<>();
    private final java.util.Map<String, String> createdMembers = new java.util.HashMap<>();
    private final Set<String> createdEnterprises = new java.util.HashSet<>();
    private final Set<String> createdMarkers = new java.util.HashSet<>();
    private final java.util.Map<String, String> createdRoles = new java.util.HashMap<>();
    private final java.util.Map<String, Set<String>> createdAssignments = new java.util.HashMap<>();

    public BusinessManagedAuthorizationTestFixture(JdbcTemplate db,
            PlatformTransactionManager transactions, ManagedInterfaceResources interfaces) {
        this.db = db;
        this.tx = new TransactionTemplate(transactions);
        try { CurrentManagedAuthorizationTestFixture.initializeSchema(db); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot initialize explicit business test schema", failure); }
        interfaces.scan();
    }

    public void domain(String domain) {
        domains.add(domain);
        tx.executeWithoutResult(t -> {
            var modes = db.queryForList("select engine_mode from summer_authorization_domain where domain_key=?", String.class, domain);
            if (modes.isEmpty()) {
                db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision) values(?,'MANAGED',0)", domain);
                createdDomains.add(domain);
            } else if (modes.size() != 1 || !"MANAGED".equals(modes.getFirst())) {
                throw new IllegalStateException("Test installation cannot replace another domain mode");
            }
            if (db.update("insert into summer_permission_bootstrap(domain_key,initialized_at) select ?,CURRENT_TIMESTAMP where not exists(select 1 from summer_permission_bootstrap where domain_key=?)", domain, domain) == 1) createdMarkers.add(domain);
            String group = id("group", domain);
            db.update("insert into summer_permission_group(domain_key,group_id,name,sort_order,version) select ?,?,'Business test fixture',0,0 where not exists(select 1 from summer_permission_group where domain_key=? and group_id=?)", domain, group, domain, group);
        });
    }

    public void identity(String domain, String subject) {
        domain(domain);
        if (db.update("insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at) select ?,'!unusable-fixture-login','USER',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_auth_user where username=?)", subject, subject) == 1) createdUsers.add(subject);
        String tenant = domain.substring(domain.indexOf(':') + 1);
        if (domain.startsWith("ENTERPRISE:")) {
            if (db.update("insert into summer_enterprise(enterprise_id,name,code,status,created_at,updated_at) select ?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_enterprise where enterprise_id=?)", tenant, tenant, tenant, tenant) == 1) createdEnterprises.add(tenant);
            String member = id("member", domain + subject);
            if (db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) select ?,?,?,?,?,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_enterprise_member where enterprise_id=? and subject=?)", tenant, member, subject, subject, subject + "@fixture.test", tenant, subject) == 1) createdMembers.put(member, domain);
        }
        String role = role(domain, subject);
        if (db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at) select ?,?,'Business fixture',?,false,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP where not exists(select 1 from summer_authorization_role where role_id=?)", role, tenant, role, role) == 1) createdRoles.put(role, domain);
        db.update("insert into summer_permission_role(domain_key,role_id) select ?,? where not exists(select 1 from summer_permission_role where domain_key=? and role_id=?)", domain, role, domain, role);
        if (db.update("insert into summer_permission_user(domain_key,username,version) select ?,?,0 where not exists(select 1 from summer_permission_user where domain_key=? and username=?)", domain, subject, domain, subject) == 1) createdAssignments.computeIfAbsent(domain, ignored -> new java.util.HashSet<>()).add(subject);
        db.update("insert into summer_permission_user_role(domain_key,username,role_id) select ?,?,? where not exists(select 1 from summer_permission_user_role where domain_key=? and username=? and role_id=?)", domain, subject, role, domain, subject, role);
    }

    /** Exact installed mappings in the named business families, never every application interface. */
    public void interfaces(String domain, String subject, Set<String> families) {
        identity(domain, subject);
        String kind = domain.substring(0, domain.indexOf(':'));
        int matched = 0;
        for (var row : db.queryForList("select resource_id,normalized_path,http_method from summer_interface_resource where active=true and configurable=true and domain_kind=?", kind)) {
            String path = row.get("normalized_path").toString();
            if (families.stream().noneMatch(f -> family(path, f))) continue;
            boolean publicRead = "GET".equals(row.get("http_method")) && (path.startsWith("/public/")
                    || path.endsWith("/system-parameters/{key}"));
            String resource = row.get("resource_id").toString();
            String permission = permission(domain, "INTERFACE", publicRead ? "PUBLIC" : "AUTHORIZED", resource);
            db.update("insert into summer_permission_resource(domain_key,permission_id,resource_type,resource_id) select ?,?,'INTERFACE',? where not exists(select 1 from summer_permission_resource where domain_key=? and permission_id=? and resource_id=?)", domain, permission, resource, domain, permission, resource);
            grant(domain, subject, permission);
            matched++;
        }
        if (matched == 0) throw new IllegalStateException("No installed business mapping matched the fixture");
        advance(domain);
    }

    private static boolean family(String path, String family) {
        return switch (family) {
            case "customers" -> path.equals("/api/customers") || path.startsWith("/api/customers/");
            case "roles" -> path.contains("/roles") || path.contains("/data-resources/");
            case "tasks" -> path.contains("/tasks");
            case "system-parameters" -> path.contains("/system-parameters") || path.contains("/system-parameter-events");
            case "dictionaries" -> path.contains("/dictionaries");
            case "monitoring" -> Set.of("/actuator/metrics", "/actuator/metrics/{requiredMetricName}",
                    "/actuator/prometheus", "/internal/v1/diagnostics/runtime",
                    "/internal/v1/diagnostics/resilience").contains(path);
            default -> throw new IllegalArgumentException("Unregistered fixture business family");
        };
    }

    public void configuration(String tenant, String resource, boolean anonymous, String... subjects) {
        String domain = "ENTERPRISE:" + tenant;
        for (String subject : subjects) {
            interfaces(domain, subject, Set.of(resource));
            for (String action : List.of("read", "create", "update", "delete"))
                data(tenant, subject, resource, action, "TENANT_ALL");
        }
        if (anonymous) publicData(tenant, resource, "TENANT_ALL");
    }

    public void data(String tenant, String subject, String resource, String action, String preset) {
        String domain = "ENTERPRISE:" + tenant;
        identity(domain, subject);
        String permission = permission(domain, "DATA", "AUTHORIZED", subject + ":" + resource + ":" + action);
        detail(domain, permission, resource, action, preset);
        grant(domain, subject, permission);
        advance(domain);
    }

    public void publicData(String tenant, String resource, String preset) {
        String domain = "ENTERPRISE:" + tenant;
        domain(domain);
        detail(domain, permission(domain, "DATA", "PUBLIC", resource + ":read"), resource, "read", preset);
        advance(domain);
    }

    public void revokeRead(String tenant, String subject, String resource) {
        String domain = "ENTERPRISE:" + tenant;
        db.update("delete from summer_role_permission where domain_key=? and role_id=? and permission_id in (select permission_id from summer_permission_data_scope where domain_key=? and data_resource_key=? and action='read')", domain, role(domain, subject), domain, resource);
        advance(domain);
    }

    public void revokePublicRead(String tenant, String resource) {
        String domain = "ENTERPRISE:" + tenant;
        db.update("update summer_permission set status='DISABLED',version=version+1 where domain_key=? and group_id=? and type='DATA' and access_mode='PUBLIC' and permission_id in (select permission_id from summer_permission_data_scope where domain_key=? and data_resource_key=? and action='read')", domain, id("group", domain), domain, resource);
        advance(domain);
    }

    public void revokeInterfaces(String domain, String subject) {
        db.update("delete from summer_role_permission where domain_key=? and role_id=? and permission_id in (select permission_id from summer_permission where domain_key=? and type='INTERFACE')", domain, role(domain, subject), domain);
        // PUBLIC resources deliberately cannot be revoked per subject: disable this fixture's public association.
        db.update("update summer_permission set status='DISABLED',version=version+1 where domain_key=? and group_id=? and type='INTERFACE' and access_mode='PUBLIC'", domain, id("group", domain));
        advance(domain);
    }

    private void detail(String domain, String permission, String resource, String action, String preset) {
        if (!Set.of("TENANT_ALL", "SELF", "DEPARTMENT").contains(preset) || !Set.of("read", "create", "update", "delete").contains(action))
            throw new IllegalArgumentException("Explicit registered DATA action and preset required");
        if (db.update("update summer_permission_data_scope set scope_preset=? where domain_key=? and permission_id=?", preset, domain, permission) == 0)
            db.update("insert into summer_permission_data_scope(domain_key,permission_id,data_resource_key,action,scope_preset) values(?,?,?,?,?)", domain, permission, resource, action, preset);
    }

    private String permission(String domain, String type, String mode, String key) {
        String permission = id(type, domain + mode + key);
        db.update("insert into summer_permission_identity(domain_key,permission_id,code,type) select ?,?,?,? where not exists(select 1 from summer_permission_identity where domain_key=? and permission_id=?)", domain, permission, permission, type, domain, permission);
        db.update("insert into summer_permission(domain_key,permission_id,code,name,type,access_mode,group_id,status,version) select ?,?,?,'Business test fixture',?,?,?,'ACTIVE',0 where not exists(select 1 from summer_permission where domain_key=? and permission_id=?)", domain, permission, permission, type, mode, id("group", domain), domain, permission);
        return permission;
    }

    private void grant(String domain, String subject, String permission) {
        String role = role(domain, subject);
        db.update("insert into summer_role_permission(domain_key,role_id,permission_id) select ?,?,? where not exists(select 1 from summer_role_permission where domain_key=? and role_id=? and permission_id=?)", domain, role, permission, domain, role, permission);
    }

    private void advance(String domain) { db.update("update summer_authorization_domain set revision=revision+1 where domain_key=? and engine_mode='MANAGED'", domain); }
    private String role(String domain, String subject) { return id("role", domain + subject); }
    private String id(String kind, String key) {
        try { return "fixture10-" + kind + "-" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((namespace + key).getBytes(StandardCharsets.UTF_8))).substring(0, 32); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Remove only this instance's generated grants/identities; never clear another fixture's facts. */
    @Override public void close() {
        tx.executeWithoutResult(t -> {
            Set<String> retainDomains = new java.util.HashSet<>();
            for (String domain : domains) {
                String group = id("group", domain);
                var permissions = db.queryForList("select permission_id from summer_permission where domain_key=? and group_id=?", String.class, domain, group);
                var roles = createdRoles.entrySet().stream().filter(e -> domain.equals(e.getValue())).map(java.util.Map.Entry::getKey).toList();
                for (String permission : permissions) {
                    db.update("delete from summer_permission_resource where domain_key=? and permission_id=?", domain, permission);
                    db.update("delete from summer_permission_data_scope where domain_key=? and permission_id=?", domain, permission);
                    db.update("delete from summer_role_permission where domain_key=? and permission_id=?", domain, permission);
                    db.update("delete from summer_permission where domain_key=? and permission_id=?", domain, permission);
                    db.update("delete from summer_permission_identity where domain_key=? and permission_id=?", domain, permission);
                }
                for (String role : roles) {
                    db.update("delete from summer_role_permission where domain_key=? and role_id=?", domain, role);
                    db.update("delete from summer_permission_user_role where domain_key=? and role_id=?", domain, role);
                    db.update("delete from summer_permission_role where domain_key=? and role_id=?", domain, role);
                    db.update("delete from summer_authorization_role where role_id=?", role);
                }
                for (String subject : createdAssignments.getOrDefault(domain, Set.of()))
                    db.update("delete from summer_permission_user where domain_key=? and username=? and not exists(select 1 from summer_permission_user_role where domain_key=? and username=?)", domain, subject, domain, subject);
                db.update("delete from summer_permission_group where domain_key=? and group_id=?", domain, group);
                boolean foreignAuthority = db.queryForObject("select count(*) from summer_permission where domain_key=?", Integer.class, domain) > 0
                        || db.queryForObject("select count(*) from summer_permission_role where domain_key=?", Integer.class, domain) > 0;
                if (foreignAuthority) retainDomains.add(domain);
                if (!foreignAuthority && createdMarkers.contains(domain)) db.update("delete from summer_permission_bootstrap where domain_key=?", domain);
                if (!foreignAuthority && createdDomains.contains(domain)) db.update("delete from summer_authorization_domain where domain_key=?", domain);
            }
            for (var member : createdMembers.entrySet())
                if (!retainDomains.contains(member.getValue())) db.update("delete from summer_enterprise_member where member_id=?", member.getKey());
            for (String tenant : createdEnterprises)
                if (!retainDomains.contains("ENTERPRISE:" + tenant)) db.update("delete from summer_enterprise where enterprise_id=?", tenant);
            for (String subject : createdUsers)
                db.update("delete from summer_auth_user where username=? and not exists(select 1 from summer_permission_user_role where username=?)", subject, subject);
        });
    }
}
