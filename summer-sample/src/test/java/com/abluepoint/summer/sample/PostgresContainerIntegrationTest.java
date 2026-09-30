package com.abluepoint.summer.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresContainerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void recreatedSchemaHasOnlyTheNewCustomerShapeAndSeed() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from summer_customer", Integer.class);
        assertThat(count).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("select username, email, display_name, active from summer_customer"))
                .containsEntry("username", "summer.demo")
                .containsEntry("email", "demo@summer.local")
                .containsEntry("display_name", "Summer Demo")
                .containsEntry("active", true);
        assertThat(jdbcTemplate.queryForList("select column_name from information_schema.columns where table_name = 'summer_customer'", String.class))
                .containsExactlyInAnyOrder("id", "username", "email", "display_name", "active", "created_at", "updated_at");
        assertThat(jdbcTemplate.queryForList("select conname from pg_constraint where conrelid = 'summer_customer'::regclass and contype = 'u'", String.class))
                .containsExactlyInAnyOrder("uk_summer_customer_username", "uk_summer_customer_email");
    }

    @Test
    void postgresEnforcesUniqueAndRequiredCustomerColumns() {
        assertThatThrownBy(() -> insert("summer.demo", "other@summer.local", "Other", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("another", "demo@summer.local", "Other", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(null, "null-user@summer.local", "Other", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("null-email", null, "Other", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("null-name", "null-name@summer.local", null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("null-active", "null-active@summer.local", "Other", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("x".repeat(65), "long-user@summer.local", "Other", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("long-email", "x".repeat(190) + "@summer.local", "Other", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("long-name", "long-name@summer.local", "x".repeat(201), true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from summer_customer", Integer.class)).isEqualTo(1);
    }

    private void insert(String username, String email, String displayName, Boolean active) {
        jdbcTemplate.update("insert into summer_customer (username, email, display_name, active, created_at, updated_at) values (?, ?, ?, ?, current_timestamp, current_timestamp)",
                username, email, displayName, active);
    }
}
