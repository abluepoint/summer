package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Runs the same public HTTP matrix after upgrading seeded V31 data on an isolated real MySQL schema. */
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","summer.security.storage.keys=memory","summer.sample.cache.enabled=false","summer.sample.resilience.enabled=false"})
@EnabledIfEnvironmentVariable(named="T19_MYSQL_PASSWORD",matches=".+")
class MultiTenantConfigurationMySqlHttpTest extends MultiTenantConfigurationHttpTest {
    private static final String SCHEMA=com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claim(MultiTenantConfigurationMySqlHttpTest.class, "summer_multi_config_"+UUID.randomUUID().toString().replace("-",""));
    private static final String URL="jdbc:mysql://127.0.0.1:3306/";
    private static Connection connect(String schema) throws SQLException { return DriverManager.getConnection(URL+schema,"root",System.getenv("T19_MYSQL_PASSWORD")); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws SQLException {
        try(var c=connect("");var st=c.createStatement()) { st.executeUpdate("CREATE DATABASE "+SCHEMA); }
        org.flywaydb.core.Flyway.configure().dataSource(URL+SCHEMA,"root",System.getenv("T19_MYSQL_PASSWORD"))
                .locations("classpath:db/migration/mysql").target("31").load().migrate();
        try(var c=connect(SCHEMA);var st=c.createStatement()) {
            st.executeUpdate("insert into summer_system_parameter(id,tenant_id,parameter_key,value_type,value_json,required_flag,public_flag,status,version,created_at,updated_at) values('preserved-param','ent-acme','preserved.key','STRING','\"old\"',true,true,'ACTIVE',0,NOW(6),NOW(6))");
            st.executeUpdate("insert into summer_dictionary_type(id,tenant_id,code,name,published,public_readable,enabled) values('preserved-dict','ent-acme','preserved-dict','Old',false,true,true)");
            st.executeUpdate("insert into summer_dictionary_item(id,tenant_id,dictionary_code,item_value,label,sort_order,enabled) values('preserved-item','ent-acme','preserved-dict','old-value','Old',1,true)");
            st.executeUpdate("insert into summer_configuration_override(id,tenant_id,resource,config_key,scope_id,created_by,owner_id,value_json,public_readable,enabled,version,updated_at) values('preserved-override','ent-acme','system-parameters','preserved.key','_tenant','old-user','old-user','\"override\"',true,true,7,NOW(6))");
        }
        registry.add("spring.datasource.url",()->URL+SCHEMA);registry.add("spring.datasource.username",()->"root");
        registry.add("spring.datasource.password",()->System.getenv("T19_MYSQL_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @Test void preservesOldRowsAndEnforcesTenantCompositeForeignKey() throws Exception {
        try(var c=connect(SCHEMA);var st=c.createStatement()) {
            try(var rs=st.executeQuery("select p.value_json,o.version,o.published_revision,r.value_json from summer_system_parameter p join summer_configuration_override o on p.tenant_id=o.tenant_id and p.parameter_key=o.config_key join summer_configuration_override_revision r on r.override_id=o.id where p.id='preserved-param'")) {
                assertThat(rs.next()).isTrue();assertThat(rs.getString(1)).isEqualTo("\"old\"");assertThat(rs.getLong(2)).isEqualTo(7);assertThat(rs.getLong(3)).isEqualTo(1);assertThat(rs.getString(4)).isEqualTo("\"override\"");
            }
            assertThatThrownBy(()->st.executeUpdate("insert into summer_dictionary_item(id,tenant_id,dictionary_code,item_value,label,sort_order,enabled) values('bad-cross-tenant','ent-foreign','preserved-dict','old-value','No',1,true)"))
                    .isInstanceOf(SQLException.class);
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
