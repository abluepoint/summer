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
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;

    @Test
    void flywayCreatesDictionaryTablesAndRealHttpReadUsesPublishedRows() throws Exception {
        members.provision("ent-acme", "dictionary-mysql-member", "dictionary-mysql-writer",
                "Dictionary writer", "dictionary-mysql@example.test");
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.subject("dictionary-mysql-writer")
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", java.util.Map.of("ent-acme", java.util.List.of("platform.dictionary.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
}
