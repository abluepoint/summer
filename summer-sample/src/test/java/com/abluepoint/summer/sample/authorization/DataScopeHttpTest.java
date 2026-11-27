package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.security.storage.keys=memory")
@Import(CurrentManagedAuthorizationTestFixture.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources resources;
    BusinessManagedAuthorizationTestFixture facts;

    private String positiveBobPriorStatus;
    @org.junit.jupiter.api.AfterEach void restorePositiveBobMembership() {
        if (positiveBobPriorStatus != null)
            db.update("update summer_enterprise_member set status=? where enterprise_id='ent-acme' and subject='bob'", positiveBobPriorStatus);
    }

    @BeforeEach void currentFacts() {
        // This scenario explicitly requires an active owner; the shared identity fixture never revives disabled members.
        positiveBobPriorStatus = db.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and subject='bob'", String.class);
        db.update("update summer_enterprise_member set status='ACTIVE' where enterprise_id='ent-acme' and subject='bob'");
        facts = new BusinessManagedAuthorizationTestFixture(db, transactions, resources);
        for (String subject : List.of("alice", "bob")) {
            facts.interfaces("ENTERPRISE:ent-acme", subject, Set.of("system-parameters"));
            facts.data("ent-acme", subject, "system-parameters", "create", "TENANT_ALL");
        }
        facts.data("ent-acme", "alice", "system-parameters", "read", "SELF");
        facts.data("ent-acme", "alice", "system-parameters", "update", "SELF");
    }
    @AfterEach void cleanup() { if (facts != null) facts.close(); }

    @Test void currentSelfRangeIsInjectedIntoParameterListDetailAndWrites() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
            .contentType("application/json").content("{\"operator\":\"AND\",\"rules\":[{\"type\":\"SELF\"}]}")
            .with(user("summer-user")).with(csrf())).andExpect(status().isNotFound());
        create("owned.by.alice", "alice");
        create("owned.by.bob", "bob");
        mvc.perform(get("/api/system-parameters").with(member("alice", "system.parameter.read")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].key").value("owned.by.alice"));
        mvc.perform(get("/api/system-parameters/owned.by.bob").with(member("alice", "system.parameter.read")))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/system-parameters/owned.by.bob").with(member("alice", "system.parameter.write"))
            .with(csrf()).contentType("application/json").content("{\"value\":\"blocked\",\"version\":0}"))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/system-parameters/owned.by.alice").with(member("alice", "system.parameter.write"))
            .with(csrf()).contentType("application/json").content("{\"value\":\"saved\",\"version\":0}"))
            .andExpect(status().isOk());
    }

    @Test void scopeAloneCannotReadManagementAndCrossTenantIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-other/data-scopes").with(user("summer-user")))
            .andExpect(status().isNotFound());
        facts.identity("ENTERPRISE:ent-acme", "reader");
        mvc.perform(get("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
            .with(member("reader", "enterprise.data-scope.read"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/enterprises/ent-acme/data-scopes/system-parameters").with(user("summer-user")))
            .andExpect(status().isNotFound());
    }

    private void create(String key, String subject) throws Exception {
        mvc.perform(post("/api/system-parameters").with(member(subject, "system.parameter.write")).with(csrf())
            .contentType("application/json").content("{\"key\":\"" + key + "\",\"type\":\"STRING\",\"value\":\"v\",\"ownerId\":\"" + subject + "\"}"))
            .andExpect(status().isCreated());
    }
    private RequestPostProcessor member(String subject, String scope) {
        return jwt().jwt(j -> j.subject(subject).claim("scope", scope)
            .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
            .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.data-scope.read", "enterprise.data-scope.write"))))
            .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
