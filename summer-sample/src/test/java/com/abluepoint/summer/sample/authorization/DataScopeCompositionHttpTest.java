package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "summer.security.storage.keys=memory")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeCompositionHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void persistsConditionTreeChecksumAndAuditAndPreviewsEffectiveScope() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator":"OR","conditions":{"operator":"AND","children":[
                                  {"field":"ORGANIZATION_TREE","operator":"IN","value":["org-engineering"]},
                                  {"field":"CREATOR","operator":"EQ","value":"alice"}
                                ]},"rules":[{"type":"TENANT","effect":"ALLOW"}]}
                                """)
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.checksum").isNotEmpty())
                .andExpect(jsonPath("$.data.conditions.operator").value("AND"));

        mvc.perform(get("/api/v1/enterprises/ent-acme/data-scopes/system-parameters/audit")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("DATA_SCOPE_POLICY_UPDATED"));

        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"alice\",\"organizationId\":\"org-engineering\"}")
                        .with(reader("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.explanation").isNotEmpty())
                .andExpect(jsonPath("$.data.policyVersion").value(1));
    }

    @Test
    void explicitUserDenyWinsAndUnknownFieldOrOperatorIsRejected() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"OR\",\"rules\":[{\"type\":\"TENANT\",\"effect\":\"ALLOW\"}]}")
                        .with(admin())).andExpect(status().isOk());

        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters/bindings/USER/alice")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[{\"type\":\"TENANT\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"alice\"}")
                        .with(reader("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.reasonCode").value("USER_DENY"));

        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"XOR\",\"rules\":[{\"type\":\"RAW_SQL\",\"effect\":\"ALLOW\"}]}")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_DATA_SCOPE_INVALID"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return member("admin", "enterprise.data-scope.read", "enterprise.data-scope.write");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader(String subject) {
        return member(subject, "enterprise.data-scope.read");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor member(String subject, String... capabilities) {
        return jwt().jwt(j -> j.subject(subject)
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", java.util.Map.of("ent-acme", java.util.List.of(capabilities))))
                .authorities(java.util.Arrays.stream(capabilities).map(v -> new SimpleGrantedAuthority("SCOPE_" + v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
