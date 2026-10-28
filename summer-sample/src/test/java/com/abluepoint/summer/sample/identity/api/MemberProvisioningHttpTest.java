package com.abluepoint.summer.sample.identity.api;

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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:member-provisioning;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.sample.authorization.legacy-scope-compatibility=false", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberProvisioningHttpTest extends IdentityHttpSupport {
    @Test void administratorAddsExistingIdentityAndCannotReactivateItByCreatingOrBindingRolesAgain() throws Exception {
        String admin = authorizeSelf(bootstrapLogin(), List.of("enterprise.members.read", "enterprise.members.write",
                "enterprise.roles.read", "enterprise.roles.write", "POST:/api/users"));
        String user = "member-" + UUID.randomUUID();
        var mapper = new tools.jackson.databind.ObjectMapper();
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", user, "password", UUID.randomUUID().toString(), "role", "USER"))))
                .andExpect(status().isOk());
        String body = mapper.writeValueAsString(Map.of("subject", user, "displayName", "New member", "email", "new@example.test"));
        String key = UUID.randomUUID().toString();
        var created = mvc.perform(post("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", key).header("X-Request-Id", "create-member")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        String member = JsonPath.read(created.getResponse().getContentAsString(), "$.data.memberId");
        mvc.perform(post("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.memberId").value(member));
        mvc.perform(get("/api/v1/enterprises/ent-acme/members/{member}/audit", member).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("MEMBER_CREATED"))
                .andExpect(jsonPath("$.data[0].requestId").value("create-member"));
        mvc.perform(post("/api/v1/enterprises/ent-acme/members/{member}:disable", member).header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", created.getResponse().getHeader("ETag")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/enterprises/ent-acme/users/{user}/roles", user).header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[],\"deniedCapabilityCodes\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/enterprises/ent-acme/members/{member}", member).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(post("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + admin)
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"missing-user\",\"displayName\":\"Missing\",\"email\":\"missing@example.test\"}"))
                .andExpect(status().isBadRequest());
    }
}
