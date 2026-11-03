package com.abluepoint.summer.sample.organization;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Runs the same public HTTP matrix on an isolated migrated schema on an isolated real MySQL schema. */
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","summer.security.storage.keys=memory","summer.sample.cache.enabled=false","summer.sample.resilience.enabled=false"})
@EnabledIfEnvironmentVariable(named="T19_MYSQL_PASSWORD",matches=".+")
class OrganizationDirectoryMySqlHttpTest extends OrganizationContextHttpTest {
    private static final String SCHEMA=com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claim(OrganizationDirectoryMySqlHttpTest.class, "summer_org_"+UUID.randomUUID().toString().replace("-",""));
    private static final String URL="jdbc:mysql://127.0.0.1:3306/";
    private static Connection connect(String schema) throws SQLException { return DriverManager.getConnection(URL+schema,"root",System.getenv("T19_MYSQL_PASSWORD")); }
    private static boolean initialized;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws SQLException {
        if (!initialized) {
            try(var c=connect("");var st=c.createStatement()) { st.executeUpdate("CREATE DATABASE "+SCHEMA); }
            org.flywaydb.core.Flyway.configure().dataSource(URL+SCHEMA,"root",System.getenv("T19_MYSQL_PASSWORD"))
                    .locations("classpath:db/migration/mysql").target("35").load().migrate();
            try(var c=connect(SCHEMA);var st=c.createStatement()) {
                st.executeUpdate("insert into summer_enterprise(enterprise_id,name,code,status,created_at,updated_at) values('ent-upgrade','Upgrade tenant','upgrade','ACTIVE',NOW(6),NOW(6))");
                st.executeUpdate("insert into summer_auth_user(username,password_hash,role,active) values('upgrade-active','not-a-login-hash','USER',true),('upgrade-disabled','not-a-login-hash','USER',false)");
                st.executeUpdate("insert into summer_authorization_user_binding(binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values ('upgrade-binding-active','ent-upgrade','upgrade-active','[]','[]',1,NOW(6),NOW(6)),('upgrade-binding-disabled','ent-upgrade','upgrade-disabled','[]','[]',1,NOW(6),NOW(6))");
            }
            initialized = true;
        }
        registry.add("spring.datasource.url",()->URL+SCHEMA);registry.add("spring.datasource.username",()->"root");
        registry.add("spring.datasource.password",()->System.getenv("T19_MYSQL_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @Test void upgradePreservesOnlyActiveLegacyLocalMemberships() throws SQLException {
        try(var c=connect(SCHEMA);var st=c.createStatement();var rs=st.executeQuery("select subject,status from summer_enterprise_member where enterprise_id='ent-upgrade'")) {
            assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("upgrade-active"); assertThat(rs.getString(2)).isEqualTo("ACTIVE"); assertThat(rs.next()).isFalse();
        }
    }
    @AfterAll static void cleanup() throws SQLException {
        try(var c=connect("");var st=c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS "+SCHEMA);
            try(var rs=st.executeQuery("select count(*) from information_schema.schemata where schema_name='"+SCHEMA+"'")) {rs.next();assertThat(rs.getInt(1)).isZero();}
        }
        com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.removed(SCHEMA);
    }
}
