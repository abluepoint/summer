package com.abluepoint.summer.sample.customer;

import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/mysql",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class CustomerMySqlFlywayIntegrationTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    private static final String SCHEMA = com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claim(
            CustomerMySqlFlywayIntegrationTest.class, "summer_customer_crud_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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
        com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.created(CustomerMySqlFlywayIntegrationTest.class, SCHEMA);
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
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void currentAuthorization() throws Exception {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "summer-client",
                (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerController")
                        && java.util.Set.of("POST /api/customers", "GET /api/customers", "GET /api/customers/{username}",
                                "PATCH /api/customers/{username}", "DELETE /api/customers/{username}")
                        .contains(method + " " + path));
    }

    @Test
    void migrationCreatesCustomerSchemaAndCanBeReappliedWithoutDroppingRows() {
        jdbcTemplate.update("insert into summer_customer (username, email, display_name, active, created_at, updated_at) values (?, ?, ?, ?, current_timestamp(6), current_timestamp(6))",
                "mysql.persisted", "mysql.persisted@summer.local", "Persisted Customer", true);

        flyway.migrate();

        assertThat(jdbcTemplate.queryForObject("select count(*) from summer_customer where username = ?", Integer.class, "mysql.persisted"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("select column_name from information_schema.columns where table_schema = database() and table_name = 'summer_customer'", String.class))
                .containsExactlyInAnyOrder("id", "username", "email", "display_name", "active", "created_at", "updated_at");
        assertThat(jdbcTemplate.queryForList("select constraint_name from information_schema.table_constraints where table_schema = database() and table_name = 'summer_customer' and constraint_type = 'UNIQUE'", String.class))
                .contains("uk_summer_customer_username", "uk_summer_customer_email");
    }

    @Test
    void realMysqlHttpCrudPreservesStateAndEnforcesUniqueFields() throws Exception {
        String writeToken = accessToken("customer.write");
        String username = "mysql.http";
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"mysql.http@summer.local\",\"displayName\":\"MySQL HTTP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "2\",\"email\":\"mysql.http@summer.local\",\"displayName\":\"Duplicate Email\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"));

        mockMvc.perform(patch("/api/customers/{username}", username)
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        String readToken = accessToken("customer.read");
        mockMvc.perform(get("/api/customers/{username}", username)
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(patch("/api/customers/{username}", username)
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + readToken)
                        .queryParam("size", "1")
                        .queryParam("sort", "username")
                        .queryParam("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(get("/api/customers/{username}", username))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/customers/{username}", username)
                        .header("Authorization", "Bearer " + writeToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/customers/{username}", username)
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isNotFound());
    }

    private String accessToken(String scope) throws Exception {
        String body = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }
}
