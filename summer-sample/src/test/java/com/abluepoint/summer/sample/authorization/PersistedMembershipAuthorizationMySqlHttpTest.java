package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Reuses the same old JWT against current persisted membership on a fresh real MySQL schema. */
@SpringBootTest(properties={"summer.sample.authorization.legacy-scope-compatibility=false","spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","summer.security.storage.keys=memory","summer.sample.cache.enabled=false","summer.sample.resilience.enabled=false"})
@EnabledIfEnvironmentVariable(named="T19_MYSQL_PASSWORD",matches=".+")
class PersistedMembershipAuthorizationMySqlHttpTest extends PersistedMembershipAuthorizationHttpTest {
    private static final String SCHEMA="summer_member_auth_"+UUID.randomUUID().toString().replace("-","");
    private static final String URL="jdbc:mysql://127.0.0.1:3306/";
    private static Connection connect(String schema) throws SQLException { return DriverManager.getConnection(URL+schema,"root",System.getenv("T19_MYSQL_PASSWORD")); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws SQLException {
        try(var c=connect("");var st=c.createStatement()) { st.executeUpdate("CREATE DATABASE "+SCHEMA); }
        registry.add("spring.datasource.url",()->URL+SCHEMA);registry.add("spring.datasource.username",()->"root");
        registry.add("spring.datasource.password",()->System.getenv("T19_MYSQL_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @AfterAll static void cleanup() throws SQLException {
        try(var c=connect("");var st=c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS "+SCHEMA);
            try(var rs=st.executeQuery("select count(*) from information_schema.schemata where schema_name='"+SCHEMA+"'")) {rs.next();assertThat(rs.getInt(1)).isZero();}
        }
    }
}
