package com.abluepoint.summer.sample.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/mysql",
        "spring.jpa.hibernate.ddl-auto=none"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class DictionaryMySqlIntegrationTest {

    private static final String SCHEMA = com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claim(
            DictionaryMySqlIntegrationTest.class, "summer_dictionary_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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
        com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.created(DictionaryMySqlIntegrationTest.class, SCHEMA);
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
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;

    @Test
    void flywayCreatesDictionaryTablesAndRealHttpReadUsesPublishedRows() throws Exception {
        members.provision("ent-acme", "dictionary-mysql-member", "dictionary-mysql-writer",
                "Dictionary writer", "dictionary-mysql@example.test");
        try (var managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                jdbcTemplate, managedTransactions, managedInterfaces)) {
            for (String action : java.util.List.of("create", "read", "update"))
                managed.data("ent-acme", "dictionary-mysql-writer", "dictionaries", action, "TENANT_ALL");
            // DraftRevisionFixture.lifecycle reads the current draft as this separate actor.
            managed.data("ent-acme", "operator", "dictionaries", "read", "TENANT_ALL");
            managed.publicData("ent-acme", "dictionaries", "TENANT_ALL");
            var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
            current.grantHandlers(domain, "dictionary-mysql-writer", (handler, path, method) ->
                    handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                            && method.equals("POST") && java.util.Set.of("/api/dictionaries",
                                    "/api/dictionaries/{code}/items", "/api/dictionaries/{code}/publish").contains(path));
            current.grantHandlers(domain, "operator", (handler, path, method) ->
                    handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                            && method.equals("GET") && path.equals("/api/dictionaries/{code}/draft"));
            // The anonymous HTTP read has its own exact PUBLIC interface permission.
            String publicRead = current.grantHandlers(domain, "dictionary-public-reader", (handler, path, method) ->
                    handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                            && method.equals("GET") && path.equals("/public/enterprises/{enterpriseId}/dictionaries/{code}"));
            new org.springframework.transaction.support.TransactionTemplate(managedTransactions).executeWithoutResult(tx -> {
                assertThat(jdbcTemplate.update("update summer_permission set access_mode='PUBLIC',version=version+1 where domain_key=? and permission_id=? and type='INTERFACE' and access_mode='AUTHORIZED' and status='ACTIVE'",
                        domain.key(), publicRead)).isEqualTo(1);
                assertThat(jdbcTemplate.update("update summer_authorization_domain set revision=revision+1 where domain_key=? and engine_mode='MANAGED'",
                        domain.key())).isEqualTo(1);
            });
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where table_schema = database() and table_name in ('summer_dictionary_type','summer_dictionary_item')",
                    Integer.class)).isEqualTo(2);

            mockMvc.perform(post("/api/dictionaries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"mysql-segment","name":"MySQL Segment","published":false,"publicReadable":true,"enabled":true}
                                    """)
                            .with(writer()))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/dictionaries/mysql-segment/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"expectedDraftRevision":0,"value":"standard","label":"Standard","sortOrder":1,"enabled":true}
                                    """)
                            .with(writer()))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/dictionaries/mysql-segment/publish").with(writer())
                    .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mockMvc, "/api/dictionaries/mysql-segment/publish", "{\"expectedVersion\":0}")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/public/enterprises/ent-acme/dictionaries/mysql-segment"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].value").value("standard"));
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.subject("dictionary-mysql-writer")
                        .claim("scope", "platform.dictionary.write")
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", java.util.Map.of("ent-acme", java.util.List.of("platform.dictionary.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
}
