package com.abluepoint.summer.sample.systemparameter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="summer.sample.parameter-events.scheduling-enabled=false")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import({com.abluepoint.summer.sample.authorization.ExplicitLegacyBusinessDomains.AcmeAndOther.class,
        com.abluepoint.summer.sample.authorization.ExplicitLegacyBusinessDomains.Platform.class})
@org.springframework.test.annotation.DirtiesContext
class SystemParameterFailureHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PublishedParameterReader published;
    @MockitoBean StringRedisTemplate redis;
    private static final String ROOT = "/api/system-parameters/";

    @BeforeEach
    void unavailableRedis() {
        // Exercise the real database path without spending the HTTP contract's timeout on Redis connection setup.
        doThrow(new RedisConnectionFailureException("Redis unavailable in HTTP contract fixture"))
                .when(redis).opsForValue();
    }

    @Test
    void invalidCreateKeepsOrderedFieldReasonsAndCreatesNoParameter() throws Exception {
        failure(post("/api/system-parameters").content("""
                {"key":"contract.invalid","type":"BOOLEAN","value":"wrong","min":2,"max":1,"pattern":"x"}
                """), 400, "SUMMER_PARAMETER_INVALID", "System parameter validation failed")
                .andExpect(jsonPath("$.error.violations.length()").value(3))
                .andExpect(jsonPath("$.error.violations[0].field").value("min"))
                .andExpect(jsonPath("$.error.violations[0].message").value("must not exceed max"))
                .andExpect(jsonPath("$.error.violations[1].field").value("value"))
                .andExpect(jsonPath("$.error.violations[1].message").value("must be true or false"))
                .andExpect(jsonPath("$.error.violations[2].field").value("pattern"))
                .andExpect(jsonPath("$.error.violations[2].message").value("is only supported for string parameters"));
        failure(get(ROOT + "contract.invalid"), 404, "SUMMER_NOT_FOUND", "System parameter not found: contract.invalid");
    }

    @Test
    void duplicateStaleUpdateAndInvalidOverrideLeaveValueAndVersionUnchanged() throws Exception {
        create("contract.update", false);
        failure(post("/api/system-parameters").content(createBody("contract.update", false)), 409,
                "SUMMER_PARAMETER_CONFLICT", "System parameter key is already in use");
        failure(patch(ROOT + "contract.update").content("{\"version\":1,\"value\":30}"), 409,
                "SUMMER_PARAMETER_VERSION_CONFLICT", "The system parameter was changed by another request");
        for (var request : List.of(patch(ROOT + "contract.update").content("{\"version\":0,\"value\":101}"),
                put(ROOT + "contract.update/overrides/org-engineering").content("{\"value\":101}"))) {
            failure(request, 400, "SUMMER_PARAMETER_INVALID", "System parameter validation failed")
                    .andExpect(jsonPath("$.error.violations.length()").value(1))
                    .andExpect(jsonPath("$.error.violations[0].field").value("value"))
                    .andExpect(jsonPath("$.error.violations[0].message").value("is above max"));
        }
        unchanged("contract.update", 1);
    }

    @Test
    void managementMissingEntriesPreserveKeyMessageAndOverrideMasksIt() throws Exception {
        String key = "contract.absent";
        for (var request : List.of(get(ROOT + key), get(ROOT + key + "/versions"),
                patch(ROOT + key).content("{\"version\":0,\"value\":30}"),
                post(ROOT + key + "/draft").content("{\"baseVersion\":0,\"idempotencyKey\":\"absent-draft\"}"),
                post(ROOT + key + "/publish").content(command(0, 0, "absent-publish")),
                post(ROOT + key + "/rollback").content(command(0, 0, "absent-rollback")),
                post(ROOT + key + "/deactivate").content(command(0, 0, "absent-deactivate")),
                post(ROOT + key + "/enable"), post(ROOT + key + "/disable"))) {
            failure(request, 404, "SUMMER_NOT_FOUND", "System parameter not found: " + key)
                    .andExpect(jsonPath("$.error.violations").isEmpty());
        }
        failure(put(ROOT + key + "/overrides/org-engineering").content("{\"value\":30}"), 404,
                "SUMMER_NOT_FOUND", "The requested resource was not found");
    }

    @Test
    void publicMissingPrivateAndInactiveValuesHaveTheSameDefaultNotFoundMessage() throws Exception {
        create("contract.private", false);
        create("contract.inactive", true);
        mvc.perform(post(ROOT + "contract.inactive/disable").with(writer())).andExpect(status().isOk());
        for (String key : List.of("contract.public-absent", "contract.private", "contract.inactive")) {
            failure(get("/public/enterprises/ent-acme/system-parameters/" + key).with(reader()), 404,
                    "SUMMER_NOT_FOUND", "The requested resource was not found")
                    .andExpect(jsonPath("$.error.violations").isEmpty());
        }
    }

    @Test
    void staleReleaseCommandsAndDraftValidationDoNotCreateRevisions() throws Exception {
        String key = "contract.stale";
        create(key, false);
        failure(post(ROOT + key + "/draft").content("{\"baseVersion\":1,\"idempotencyKey\":\"stale-draft\"}"),
                409, "SUMMER_PARAMETER_VERSION_CONFLICT", "The system parameter was changed by another request");
        for (String operation : List.of("publish", "rollback", "deactivate")) {
            failure(post(ROOT + key + "/" + operation).content(command(0, 1, "stale-" + operation)),
                    409, "SUMMER_PARAMETER_VERSION_CONFLICT", "The system parameter was changed by another request");
        }
        failure(post(ROOT + key + "/draft").content("{\"baseVersion\":0,\"value\":101,\"idempotencyKey\":\"invalid-draft\"}"),
                400, "SUMMER_PARAMETER_INVALID", "System parameter release validation failed")
                .andExpect(jsonPath("$.error.violations[0].field").value("value"))
                .andExpect(jsonPath("$.error.violations[0].message").value("is above max"));
        unchanged(key, 1);
    }

    @Test
    void invalidRevisionAndIdempotencyFieldsRetainTheirRequestViolation() throws Exception {
        String key = "contract.release-fields";
        create(key, false);
        for (String operation : List.of("draft", "publish", "rollback", "deactivate")) {
            failure(post(ROOT + key + "/" + operation).content("{\"baseVersion\":0,\"expectedVersion\":0,\"idempotencyKey\":\" \"}"),
                    400, "SUMMER_PARAMETER_INVALID", "idempotencyKey is required")
                    .andExpect(jsonPath("$.error.violations[0].field").value("request"))
                    .andExpect(jsonPath("$.error.violations[0].message").value("idempotencyKey is required"));
        }
        for (String operation : List.of("publish", "rollback")) {
            for (String revision : List.of("null", "-1")) {
                failure(post(ROOT + key + "/" + operation).content("{\"revision\":" + revision +
                                ",\"expectedVersion\":0,\"idempotencyKey\":\"invalid-" + operation + revision + "\"}"),
                        400, "SUMMER_PARAMETER_INVALID", "revision is required")
                        .andExpect(jsonPath("$.error.violations.length()").value(1))
                        .andExpect(jsonPath("$.error.violations[0].field").value("request"))
                        .andExpect(jsonPath("$.error.violations[0].message").value("revision is required"));
            }
        }
        unchanged(key, 1);
    }

    @Test
    void missingRevisionAndReleaseStateConflictsRetainTheirDistinctMessages() throws Exception {
        String key = "contract.release-state";
        create(key, false);
        for (String operation : List.of("publish", "rollback")) {
            failure(post(ROOT + key + "/" + operation).content(command(999, 0, "missing-" + operation)),
                    404, "SUMMER_NOT_FOUND", "System parameter version not found: " + key + ":999");
        }
        mvc.perform(post(ROOT + key + "/draft").with(writer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":0,\"value\":30,\"idempotencyKey\":\"state-draft\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.revision").value(1));
        failure(post(ROOT + key + "/publish").content(command(0, 0, "not-draft")),
                409, "SUMMER_PARAMETER_VERSION_CONFLICT", "Only a draft can be published");
        failure(post(ROOT + key + "/rollback").content(command(1, 0, "is-draft")),
                409, "SUMMER_PARAMETER_VERSION_CONFLICT", "Draft versions cannot be rolled back to");
        unchanged(key, 2);
    }

    @Test
    void missingAndOtherTenantEventRetryStayNotFoundWithoutMutatingTheEvent() throws Exception {
        failure(post("/api/system-parameter-events/unknown-event/retry"), 404,
                "SUMMER_NOT_FOUND", "System parameter event not found: unknown-event");
        create("contract.event", false);
        var events = mvc.perform(get("/api/system-parameter-events").queryParam("key", "contract.event").with(reader()))
                .andExpect(status().isOk()).andReturn();
        var before = json.readTree(events.getResponse().getContentAsString()).path("data").path("items").get(0);
        String id = before.path("eventId").asString();
        mvc.perform(post("/api/v1/enterprises/ent-other/system-parameter-events/" + id + "/retry")
                        .header("X-Request-Id", "other-event").with(actor("ent-other", "SCOPE_system.parameter.write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("System parameter event not found: " + id))
                .andExpect(jsonPath("$.requestId").value("other-event"))
                .andExpect(header().string("X-Request-Id", "other-event"));
        mvc.perform(get("/api/system-parameter-events").queryParam("key", "contract.event").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value(before.path("status").asString()))
                .andExpect(jsonPath("$.data.items[0].attempts").value(before.path("attempts").asInt()));
    }

    @Test
    void publishedReaderRecoversOnlyAbsenceAndKeepsPermissionFailuresVisible() throws Exception {
        create("contract.reader-private", false);
        create("contract.reader-public", true);
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("reader-contract")
                .header("alg", "RS256").subject("operator").claim("scope", "system.parameter.read")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).build();
        var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,
                List.of(new SimpleGrantedAuthority("SCOPE_system.parameter.read")));
        org.assertj.core.api.Assertions.assertThat(published.read("contract.reader-absent", auth)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(published.read("contract.reader-private", auth)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(published.read("contract.reader-public", auth)).hasValue(new PublishedParameterReader.Value(25, 0));
        var deniedToken = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("denied-contract")
                .header("alg", "RS256").subject("unrelated-reader").claim("enterprise_memberships", List.of()).build();
        var denied = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(deniedToken, List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> published.read("ent-acme", "contract.reader-public", denied))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void concurrentStatusRequestsKeepOptimisticConflictsAndCommitOnlySuccessfulChanges() throws Exception {
        for (String operation : List.of("enable", "disable")) {
            String key = "contract.concurrent-" + operation;
            create(key, false);
            int successful = 0;
            int conflicts = 0;
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
                for (int attempt = 0; attempt < 3 && conflicts == 0; attempt++) {
                    var start = new java.util.concurrent.CountDownLatch(1);
                    var futures = new java.util.ArrayList<java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse>>();
                    for (int index = 0; index < 8; index++) {
                        String correlation = "status-" + operation + "-" + attempt + "-" + index;
                        futures.add(executor.submit(() -> {
                            start.await();
                            return mvc.perform(post(ROOT + key + "/" + operation).with(writer())
                                    .header("X-Request-Id", correlation)).andReturn().getResponse();
                        }));
                    }
                    start.countDown();
                    for (var future : futures) {
                        var response = future.get(20, java.util.concurrent.TimeUnit.SECONDS);
                        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isIn(200, 409);
                        var body = json.readTree(response.getContentAsString());
                        org.assertj.core.api.Assertions.assertThat(body.path("requestId").asString())
                                .isNotBlank().startsWith("status-" + operation + "-").isEqualTo(response.getHeader("X-Request-Id"));
                        if (response.getStatus() == 200) successful++;
                        else {
                            conflicts++;
                            org.assertj.core.api.Assertions.assertThat(body.path("error").path("code").asString())
                                    .isEqualTo("SUMMER_PARAMETER_VERSION_CONFLICT");
                            org.assertj.core.api.Assertions.assertThat(body.path("error").path("message").asString())
                                    .isEqualTo("The system parameter was changed by another request");
                            org.assertj.core.api.Assertions.assertThat(body.path("error").path("violations").size()).isZero();
                        }
                    }
                }
            }
            org.assertj.core.api.Assertions.assertThat(conflicts).as(operation + " produces a real optimistic conflict").isPositive();
            mvc.perform(get(ROOT + key).with(reader())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.value").value(25))
                    .andExpect(jsonPath("$.data.version").value(successful))
                    .andExpect(jsonPath("$.data.status").value(operation.equals("enable") ? "ACTIVE" : "INACTIVE"));
        }
    }

    private ResultActions failure(MockHttpServletRequestBuilder request, int status, String code, String message) throws Exception {
        return mvc.perform(request.with(writer()).header("X-Request-Id", "parameter-contract").with(value -> {
                    if (value.getContentLengthLong() > 0) value.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    return value;
                }))
                .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message)).andExpect(jsonPath("$.requestId").value("parameter-contract"))
                .andExpect(header().string("X-Request-Id", "parameter-contract"))
                .andExpect(jsonPath("$.error.cause").doesNotExist()).andExpect(jsonPath("$.error.context").doesNotExist());
    }
    private void create(String key, boolean publicValue) throws Exception {
        mvc.perform(post("/api/system-parameters").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(key, publicValue))).andExpect(status().isCreated());
    }
    private static String createBody(String key, boolean publicValue) {
        return "{\"key\":\"" + key + "\",\"type\":\"INTEGER\",\"value\":25,\"min\":1,\"max\":100,\"public\":" + publicValue + "}";
    }
    private void unchanged(String key, int revisions) throws Exception {
        mvc.perform(get(ROOT + key).with(reader())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(25)).andExpect(jsonPath("$.data.version").value(0));
        mvc.perform(get(ROOT + key + "/versions").with(reader())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(revisions)).andExpect(jsonPath("$.data.currentVersion").value(0));
    }
    private static String command(long revision, long version, String idempotency) {
        return "{\"revision\":" + revision + ",\"expectedVersion\":" + version + ",\"idempotencyKey\":\"" + idempotency + "\"}";
    }
    private static RequestPostProcessor writer() { return actor("ent-acme", "SCOPE_system.parameter.write", "SCOPE_system.parameter.read"); }
    private static RequestPostProcessor reader() { return actor("ent-acme", "SCOPE_system.parameter.read"); }
    private static RequestPostProcessor actor(String tenant, String... scopes) {
        return jwt().jwt(token -> token.subject("operator").claim("scope", java.util.Arrays.stream(scopes).map(s -> s.replaceFirst("^SCOPE_", "")).collect(java.util.stream.Collectors.joining(" "))).claim("enterprise_memberships",
                List.of(Map.of("enterpriseId", tenant, "status", "ACTIVE"))))
                .authorities(java.util.Arrays.stream(scopes).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));
    }
}
