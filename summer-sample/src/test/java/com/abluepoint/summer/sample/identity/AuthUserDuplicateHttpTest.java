package com.abluepoint.summer.sample.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:identity-duplicate;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.sample.authorization.legacy-scope-compatibility=false", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthUserDuplicateHttpTest extends IdentityHttpSupport {
    @Test void duplicateUsernameIsAControlledConflictAndOriginalUserRemainsUsable() throws Exception {
        String admin = authorizeSelf(bootstrapLogin(), List.of("enterprise.members.read", "enterprise.members.write",
                "POST:/api/users", "GET:/api/users"));
        String username = "unique-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        String body = new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("username", username, "password", password, "role", "USER"));
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin).header("X-Request-Id", "duplicate-user")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SUMMER_USER_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("duplicate-user"));
        org.assertj.core.api.Assertions.assertThat(login(username, password) != null).isTrue();
    }
}
