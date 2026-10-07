package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import java.sql.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "summer.security.storage.keys=memory", "summer.sample.cache.enabled=false", "summer.sample.resilience.enabled=false"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="T19_MYSQL_PASSWORD",matches=".+")
class ConfigurationMySqlHttpTest {
    private static final String SCHEMA="summer_t19_"+UUID.randomUUID().toString().replace("-","");
    private static final String URL="jdbc:mysql://127.0.0.1:3306/";
    private static Connection connection() throws SQLException { return DriverManager.getConnection(URL,"root",System.getenv("T19_MYSQL_PASSWORD")); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection c=connection(); Statement st=c.createStatement()) { st.executeUpdate("CREATE DATABASE "+SCHEMA); }
        registry.add("spring.datasource.url",()->URL+SCHEMA);
        registry.add("spring.datasource.username",()->"root");
        registry.add("spring.datasource.password",()->System.getenv("T19_MYSQL_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @AfterAll static void cleanup() throws SQLException {
        try (Connection c=connection(); Statement st=c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS "+SCHEMA);
            try (ResultSet rows=st.executeQuery("select count(*) from information_schema.schemata where schema_name='"+SCHEMA+"'")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
        }
    }
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void migratesAndEnforcesScopesAndOverridesThroughHttp() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version in ('29','30') and success=1",Integer.class)).isEqualTo(2);
        var actor=jwt().jwt(j->j.subject("mysql-user").claim("organization_id","org-engineering")
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
                .claim("enterprise_capabilities",Map.of("ent-acme",List.of("enterprise.data-scope.write","enterprise.data-scope.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"),new SimpleGrantedAuthority("SCOPE_system.parameter.read"),new SimpleGrantedAuthority("SCOPE_enterprise.data-scope.write"),new SimpleGrantedAuthority("SCOPE_enterprise.data-scope.read"));
        mvc.perform(post("/api/system-parameters").with(actor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"mysql.scope\",\"type\":\"STRING\",\"value\":\"base\",\"public\":true}")) .andExpect(status().isCreated());
        mvc.perform(put("/api/system-parameters/mysql.scope/overrides/org-engineering").with(actor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"org\",\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(get("/public/system-parameters/mysql.scope").with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("org"));
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters/bindings/USER/mysql-user").with(actor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}")) .andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters").with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/public/system-parameters/mysql.scope").with(actor)).andExpect(status().isNotFound());
    }
}
