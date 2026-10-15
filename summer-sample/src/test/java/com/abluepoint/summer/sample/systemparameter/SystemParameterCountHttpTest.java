package com.abluepoint.summer.sample.systemparameter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"summer.security.storage.keys=memory", "summer.sample.authorization.legacy-scope-compatibility=true"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemParameterCountHttpTest {
    private static final String ROOT = "/api/v1/enterprises/ent-acme/system-parameters";
    private final String prefix = "count." + UUID.randomUUID().toString().replace("-", "");
    @Autowired MockMvc http;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired org.springframework.core.env.Environment configuration;

    @BeforeEach
    void realRedisDependencyIsReadyBeforeContractAssertions() {
        assertTrue(!configuration.getProperty("spring.data.redis.password", "").isBlank(),
                "Inject the Redis password through the test process environment");
        try (var connection = redis.getConnectionFactory().getConnection()) {
            assertEquals("PONG", connection.ping());
        }
    }

    @Test
    void countUsesTheSameCombinedFiltersAsListAndIgnoresPageWindow() throws Exception {
        create("one", "STRING", "\"one\"", true);
        create("two", "STRING", "\"two\"", true);
        create("number", "INTEGER", "4", true);
        create("private", "STRING", "\"private\"", false);
        create("inactive", "STRING", "\"inactive\"", true);
        http.perform(post(ROOT + "/" + prefix + ".inactive/disable").with(auth("system.parameter.write")))
                .andExpect(status().isOk());
        String query = "?key=" + prefix + "&type=STRING&status=ACTIVE&public=true&size=1&page=1&sort=key&direction=desc&projection=summary";
        http.perform(get(ROOT + query).with(auth("system.parameter.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        http.perform(get(ROOT + ":count" + query).with(auth("system.parameter.read"))
                        .header("X-Request-Id", "count-combined"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string("X-Request-Id", "count-combined"))
                .andExpect(jsonPath("$.requestId").value("count-combined"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items").doesNotExist());
        http.perform(get(ROOT + ":count?key=" + prefix + "&type=STRING&status=ACTIVE&public=true&page=99&size=1")
                        .with(auth("system.parameter.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void legacyAliasAndEmptyCountPreserveLegalDetailKeys() throws Exception {
        create("count", "STRING", "\"detail\"", false);
        http.perform(post(ROOT).with(auth("system.parameter.write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"count\",\"type\":\"STRING\",\"value\":\"legal detail\"}"))
                .andExpect(status().isCreated());
        http.perform(get("/api/system-parameters:count?tenantId=ent-acme&key=" + prefix)
                        .with(auth("system.parameter.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        http.perform(get(ROOT + "/" + prefix + ".count").with(auth("system.parameter.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("detail"));
        http.perform(get(ROOT + "/count").with(auth("system.parameter.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("legal detail"));
        http.perform(get(ROOT + ":count?key=" + prefix + ".missing").with(auth("system.parameter.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void countRejectsTheSameUnknownAndInvalidParametersAsList() throws Exception {
        for (String query : List.of("extra=x", "sort=value", "direction=sideways", "projection=bean", "page=-1", "size=0", "size=101", "type=TEXT", "status=DELETED", "public=yes", "key=INVALID")) {
            http.perform(get(ROOT + ":count?" + query).with(auth("system.parameter.read"))
                            .header("X-Request-Id", "count-invalid"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                    .andExpect(header().string("X-Request-Id", "count-invalid"))
                    .andExpect(jsonPath("$.requestId").value("count-invalid"));
        }
    }

    @Test
    void countRejectsMissingReadScopeAndAmbiguousOrUnauthorizedTenants() throws Exception {
        for (String path : List.of(ROOT + ":count?tenantId=ent-other", ROOT + ":count?tenantId=ent-acme&tenantId=ent-other", "/api/v1/enterprises/ent-other/system-parameters:count")) {
            http.perform(get(path).with(auth("system.parameter.read")).header("X-Request-Id", "count-forbidden"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                    .andExpect(jsonPath("$.requestId").value("count-forbidden"));
        }
        http.perform(get(ROOT + ":count").with(auth("system.parameter.write")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void countAndListApplyTheSameDenyingDataScope() throws Exception {
        create("scoped", "STRING", "\"scoped\"", false);
        try {
            scope("{\"type\":\"TENANT\",\"effect\":\"DENY\"}");
            http.perform(get(ROOT + ":count?key=" + prefix).with(auth("system.parameter.read")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
            http.perform(get(ROOT + "?key=" + prefix).with(auth("system.parameter.read")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.items.length()").value(0));
            http.perform(get(ROOT + "/" + prefix + ".scoped").with(auth("system.parameter.read")))
                    .andExpect(status().isNotFound());
        } finally {
            scope("{\"type\":\"TENANT\"}");
        }
    }

    private void scope(String rule) throws Exception {
        http.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
                        .with(auth("enterprise.data-scope.write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"AND\",\"rules\":[" + rule + "]}"))
                .andExpect(status().isOk());
    }

    private void create(String suffix, String type, String value, boolean publicValue) throws Exception {
        http.perform(post(ROOT).with(auth("system.parameter.write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + prefix + "." + suffix + "\",\"type\":\"" + type + "\",\"value\":" + value + ",\"public\":" + publicValue + "}"))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor auth(String scope) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject("count-fixture").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.data-scope.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
