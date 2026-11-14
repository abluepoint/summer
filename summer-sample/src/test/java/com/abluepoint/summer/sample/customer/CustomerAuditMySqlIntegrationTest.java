package com.abluepoint.summer.sample.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/mysql",
        "spring.jpa.hibernate.ddl-auto=none"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class CustomerAuditMySqlIntegrationTest {

    private static final String SCHEMA = com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claim(
            CustomerAuditMySqlIntegrationTest.class, "summer_customer_audit_" + java.util.UUID.randomUUID().toString().replace("-", ""));
    private static boolean ownedSchemaCreated;
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName(SCHEMA);

    @org.junit.jupiter.api.BeforeAll
    static void recordCreatedSchema() throws java.sql.SQLException {
        org.assertj.core.api.Assertions.assertThat(MYSQL.isRunning()).isTrue();
        org.assertj.core.api.Assertions.assertThat(MYSQL.getDatabaseName()).isEqualTo(SCHEMA);
        try (var connection = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            org.assertj.core.api.Assertions.assertThat(connection.getCatalog()).isEqualTo(SCHEMA);
            try (var rows = statement.executeQuery("select count(*) from information_schema.schemata where schema_name='" + SCHEMA + "'")) {
                rows.next();
                org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
        com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.created(CustomerAuditMySqlIntegrationTest.class, SCHEMA);
        ownedSchemaCreated = true;
    }

    @org.junit.jupiter.api.AfterAll
    static void removeOwnedSchema() throws java.sql.SQLException {
        if (!ownedSchemaCreated) return;
        try (var connection = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            org.assertj.core.api.Assertions.assertThat(MYSQL.getDatabaseName()).isEqualTo(SCHEMA);
            org.assertj.core.api.Assertions.assertThat(connection.getCatalog()).isEqualTo(SCHEMA);
            statement.executeUpdate("DROP DATABASE IF EXISTS " + SCHEMA);
            try (var rows = statement.executeQuery("select count(*) from information_schema.schemata where schema_name='" + SCHEMA + "'")) {
                rows.next();
                org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isZero();
            }
        }
        com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.removed(SCHEMA);
        ownedSchemaCreated = false;
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void currentAuthorization() throws Exception {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "mysql-operator",
                (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerController")
                        && method.equals("POST") && path.equals("/api/customers"));
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "mysql-auditor",
                (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerAuditController")
                        && method.equals("GET") && path.equals("/api/audit-events"));
    }

    @Test
    void flywayCreatesAuditTableAndRealHttpWriteIsQueryable() throws Exception {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_schema = database() and table_name = 'summer_audit_event'",
                String.class);
        // V38 adds nullable trace correlation; later additive migrations must remain valid.
        org.assertj.core.api.Assertions.assertThat(columns).contains(
                "event_id", "tenant_id", "organization_id", "principal", "resource", "action",
                "result", "request_id", "detail_json", "occurred_at", "trace_id");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "mysql-audit-request")
                        .content("{\"username\":\"mysql.audit\",\"email\":\"mysql.audit@summer.local\",\"displayName\":\"MySQL Audit\"}")
                        .with(customerWriter()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit-events")
                        .queryParam("requestId", "mysql-audit-request")
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[*].action").value(hasItem("CUSTOMER_CREATED")));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor customerWriter() {
        return jwt().jwt(token -> token.subject("mysql-operator").claim("scope", "customer.write"))
                .authorities(new SimpleGrantedAuthority("SCOPE_customer.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auditReader() {
        return jwt().jwt(token -> token.subject("mysql-auditor").claim("scope", "enterprise.audit.read"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }
}
