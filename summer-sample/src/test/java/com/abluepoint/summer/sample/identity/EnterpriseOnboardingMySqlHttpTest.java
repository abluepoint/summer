package com.abluepoint.summer.sample.identity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Exercises V36 -> V37 on real MySQL, then the same real login and HTTP onboarding seam. */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none",
        "summer.security.storage.keys=memory", "summer.sample.cache.enabled=false", "summer.sample.resilience.enabled=false"})
@EnabledIfEnvironmentVariable(named = "T19_MYSQL_PASSWORD", matches = ".+")
class EnterpriseOnboardingMySqlHttpTest extends EnterpriseOnboardingHttpTest {
    private static final String SCHEMA =com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claim(EnterpriseOnboardingMySqlHttpTest.class,  "summer_identity_upgrade_" + UUID.randomUUID().toString().replace("-", ""));
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/";
    private static Connection connect(String schema) throws SQLException {
        return DriverManager.getConnection(URL + schema, "root", System.getenv("T19_MYSQL_PASSWORD"));
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws SQLException {
        try (var c = connect(""); var statement = c.createStatement()) { statement.executeUpdate("CREATE DATABASE " + SCHEMA); }
        org.flywaydb.core.Flyway.configure().dataSource(URL + SCHEMA, "root", System.getenv("T19_MYSQL_PASSWORD"))
                .locations("classpath:db/migration/mysql").target("36").load().migrate();
        registry.add("spring.datasource.url", () -> URL + SCHEMA);
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv("T19_MYSQL_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
    @AfterAll static void cleanup() throws SQLException {
        try (var c = connect(""); var statement = c.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + SCHEMA);
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + SCHEMA + "'")) {
                result.next(); org.assertj.core.api.Assertions.assertThat(result.getInt(1)).isZero();
            }
        }
        com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.removed(SCHEMA);
    }
}
