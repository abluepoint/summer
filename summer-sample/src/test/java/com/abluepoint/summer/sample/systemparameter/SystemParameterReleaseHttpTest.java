package com.abluepoint.summer.sample.systemparameter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemParameterReleaseHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void draftsDoNotChangeRuntimeAndPublishRollbackAreVersionedAndIdempotent() throws Exception {
        mockMvc.perform(post("/api/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"release.timeout","type":"INTEGER","value":25,"required":true,"min":1,"max":100}
                                """)
                        .with(writer()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/system-parameters/release.timeout/draft")
                        .header("X-Request-Id", "draft-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":30,"required":true,"min":1,"max":100,"baseVersion":0,"idempotencyKey":"draft-key"}
                                """)
                        .with(writer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.state").value("DRAFT"))
                .andExpect(jsonPath("$.data.revision").value(1));

        mockMvc.perform(get("/api/system-parameters/release.timeout").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(25));

        mockMvc.perform(post("/api/system-parameters/release.timeout/publish")
                        .header("X-Request-Id", "publish-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":1,"expectedVersion":0,"idempotencyKey":"publish-key"}""")
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(30))
                .andExpect(jsonPath("$.data.state").value("PUBLISHED"));

        mockMvc.perform(post("/api/system-parameters/release.timeout/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":1,"expectedVersion":0,"idempotencyKey":"stale-publish"}""")
                        .with(writer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PARAMETER_VERSION_CONFLICT"));

        mockMvc.perform(post("/api/system-parameters/release.timeout/rollback")
                        .header("X-Request-Id", "rollback-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"expectedVersion":1,"idempotencyKey":"rollback-key"}""")
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(25))
                .andExpect(jsonPath("$.data.state").value("ROLLED_BACK"));

        mockMvc.perform(post("/api/system-parameters/release.timeout/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"expectedVersion":2,"idempotencyKey":"deactivate-key"}""")
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.state").value("INACTIVE"));

        mockMvc.perform(get("/api/system-parameters/release.timeout/versions").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].state").value(hasItem("DRAFT")))
                .andExpect(jsonPath("$.data.items[*].state").value(hasItem("PUBLISHED")))
                .andExpect(jsonPath("$.data.items[*].state").value(hasItem("ROLLED_BACK")))
                .andExpect(jsonPath("$.data.currentVersion").value(3));
    }

    @Test
    void releaseRequiresWriteAuthorityAndRejectsInvalidRevision() throws Exception {
        mockMvc.perform(post("/api/system-parameters/release.timeout/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":99,"expectedVersion":0,"idempotencyKey":"nope"}""")
                        .with(reader()))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("reader"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"));
    }
}
