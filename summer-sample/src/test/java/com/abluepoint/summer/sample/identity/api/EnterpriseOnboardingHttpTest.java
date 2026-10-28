package com.abluepoint.summer.sample.identity.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:enterprise-onboarding;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.sample.authorization.legacy-scope-compatibility=false", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EnterpriseOnboardingHttpTest extends IdentityHttpSupport {
    @Test void creatorCanManageOnlyTheirNewTenantsAfterFreshLoginAndCreationIsAudited() throws Exception {
        String token = bootstrapLogin();
        var mapper = new tools.jackson.databind.ObjectMapper();
        var caps = new ArrayList<String>();
        for (String action : List.of("read", "write")) {
            var r = mvc.perform(post("/api/v1/enterprises/ent-acme/capabilities").header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("code", "platform.enterprise." + action, "resource", "enterprise", "action", action, "scope", "PLATFORM"))))
                    .andExpect(status().isCreated()).andReturn();
            caps.add(JsonPath.read(r.getResponse().getContentAsString(), "$.data.capabilityId"));
        }
        token = authorizeSelf(token, caps);
        var tenants = new ArrayList<String>();
        for (int index = 0; index < 2; index++) {
            String rid = "creator-onboarding-" + index;
            var created = mvc.perform(post("/api/v1/platform/enterprises").header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", rid).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("name", "Owned tenant", "code", "owned-" + UUID.randomUUID()))))
                    .andExpect(status().isCreated()).andReturn();
            String tenant = JsonPath.read(created.getResponse().getContentAsString(), "$.data.enterpriseId");
            tenants.add(tenant);
            // Existing signed tokens do not acquire a new tenant membership implicitly.
            mvc.perform(get("/api/v1/enterprises/{tenant}/members", tenant).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            token = bootstrapLogin();
            mvc.perform(post("/api/v1/enterprises/{tenant}/org-units", tenant).header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"First organization\",\"code\":\"FIRST\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/v1/enterprises/{tenant}/members", tenant).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
            mvc.perform(get("/api/v1/enterprises/{tenant}/roles", tenant).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
            mvc.perform(get("/api/v1/platform/enterprises/{tenant}/audit", tenant).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].action").value("ENTERPRISE_CREATED"))
                    .andExpect(jsonPath("$.data[0].requestId").value(rid));
        }
        for (String tenant : tenants) {
            mvc.perform(get("/api/v1/enterprises/{tenant}/org-tree", tenant).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        }
        mvc.perform(get("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
