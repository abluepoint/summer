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
class DictionaryMySqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Test
    void flywayCreatesDictionaryTablesAndRealHttpReadUsesPublishedRows() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name in ('summer_dictionary_type','summer_dictionary_item')",
                Integer.class)).isEqualTo(2);

        mockMvc.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"mysql-segment","name":"MySQL Segment","published":true,"publicReadable":true,"enabled":true}
                                """)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dictionaries/mysql-segment/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"standard","label":"Standard","sortOrder":1,"enabled":true}
                                """)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/public/dictionaries/mysql-segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].value").value("standard"));
    }
}
