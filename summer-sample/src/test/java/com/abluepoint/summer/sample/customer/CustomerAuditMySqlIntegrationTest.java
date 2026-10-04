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
class CustomerAuditMySqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Test
    void flywayCreatesAuditTableAndRealHttpWriteIsQueryable() throws Exception {
        int columns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = database() and table_name = 'summer_audit_event'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(columns).isEqualTo(10);

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
        return jwt().jwt(token -> token.subject("mysql-operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_customer.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auditReader() {
        return jwt().jwt(token -> token.subject("mysql-auditor"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }
}
