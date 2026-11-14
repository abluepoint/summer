package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.common.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "summer.security.storage.keys=memory")
@AutoConfigureMockMvc
@Import({com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class, com.abluepoint.summer.sample.testsupport.ExactCurrentHttpTestFixture.class, UnifiedExceptionHttpTest.Endpoint.class, UnifiedExceptionHttpTest.Messages.class})
@org.springframework.test.annotation.DirtiesContext
class UnifiedExceptionHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void currentAuthorization() throws Exception {
        current.grantHandlers(Domain.platform(), "user", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.UnifiedExceptionHttpTest$Endpoint")
                        && java.util.Set.of("GET /api/exception-pilot/{mode}").contains(method + " " + path));
        current.grantHandlers(Domain.platform(), "exception-pilot", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.UnifiedExceptionHttpTest$Endpoint")
                        && java.util.Set.of("GET /api/exception-pilot/{mode}").contains(method + " " + path));
        current.grantHandlers(Domain.platform(), "user", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerAuditController")
                        && java.util.Set.of("GET /api/audit-events/{eventId}").contains(method + " " + path));
        current.grantHandlers(Domain.platform(), "exception-pilot", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerAuditController")
                        && java.util.Set.of("GET /api/audit-events/{eventId}").contains(method + " " + path));
    }

    @Autowired MockMvc mvc;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
    @Autowired com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> keys;

    @Test void realHttpCarriesLiteralAndAuditErrorsThroughTheSecurityChain() throws Exception {
        var now = java.time.Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("http://127.0.0.1:8080").subject("exception-pilot").issuedAt(now).expiresAt(now.plusSeconds(120))
                .claim("scope", "customer.read").build();
        var token = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(keys).encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(
                                org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            for (String[] example : new String[][]{{"/api/exception-pilot/literal", "  literal {0}  "},
                    {"/api/audit-events/absent-pilot-event", "Audit event not found"}}) {
                var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                "http://127.0.0.1:" + port + example[0])).timeout(java.time.Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token).header("X-Request-Id", "pilot-tcp")
                        .header("Accept-Language", "fr").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(404);
                var body = new tools.jackson.databind.ObjectMapper().readTree(response.body());
                org.assertj.core.api.Assertions.assertThat(body.path("error").path("message").asString()).isEqualTo(example[1]);
                org.assertj.core.api.Assertions.assertThat(body.path("requestId").asString()).isEqualTo("pilot-tcp");
                org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("X-Request-Id")).contains("pilot-tcp");
            }
        }
    }

    @Test void literalMessageOverridesTranslationAndRetainsCorrelation() throws Exception {
        mvc.perform(get("/api/exception-pilot/literal").with(jwt()).header("X-Request-Id", "pilot-literal")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "pilot-literal"))
                .andExpect(jsonPath("$.requestId").value("pilot-literal"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("  literal {0}  "));
    }

    @Test void absentMessagesUseRequestLocaleAndDefaultWhileLiteralBlanksStayLiteral() throws Exception {
        for (String[] example : new String[][]{{"en", "Missing item"}, {"fr", "Absent item"}}) {
            mvc.perform(get("/api/exception-pilot/absent").with(jwt()).header("Accept-Language", example[0]))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.message").value(example[1]));
        }
        for (String[] example : new String[][]{{"empty", ""}, {"blank", "   "}, {"fallback", "The request conflicts with current state"}}) {
            mvc.perform(get("/api/exception-pilot/" + example[0]).with(jwt()))
                    .andExpect(jsonPath("$.error.message").value(example[1]));
        }
    }

    @Test void unknownTechnicalFailuresAreSafeAndGenerateCorrelatedRequestIds() throws Exception {
        var result = mvc.perform(get("/api/exception-pilot/unknown").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(CommonErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist()).andReturn();
        var body = new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.path("requestId").asString())
                .isNotBlank().isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).doesNotContain("private diagnostic");
    }

    @Test void missingAuditEventKeepsItsOriginalCodeStatusAndMessage() throws Exception {
        mvc.perform(get("/api/audit-events/absent-pilot-event").with(jwt().jwt(token -> token.claim("scope", "customer.read")).authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read")))
                        .header("X-Request-Id", "pilot-audit"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Audit event not found"))
                .andExpect(header().string("X-Request-Id", "pilot-audit"))
                .andExpect(jsonPath("$.requestId").value("pilot-audit"));
    }

    @Test void typedStatusMappingsPreserveNonCategoryStatuses() throws Exception {
        for (String[] example : new String[][]{{"PRECONDITION_FAILED", "412", "SUMMER_PRECONDITION_FAILED"},
                {"OAUTH_CLIENT_VERSION_CONFLICT", "412", "SUMMER_OAUTH_CLIENT_VERSION_CONFLICT"},
                {"BFF_UNAVAILABLE", "503", "BFF_SUMMER_UNAVAILABLE"},
                {"SESSION_STORE_UNAVAILABLE", "503", "SUMMER_SESSION_STORE_UNAVAILABLE"},
                {"DEPENDENCY_UNAVAILABLE", "503", "SUMMER_DEPENDENCY_UNAVAILABLE"},
                {"DICTIONARY_SSE_LIMIT", "429", "SUMMER_DICTIONARY_SSE_LIMIT"},
                {"TASK_SSE_LIMIT", "429", "SUMMER_TASK_SSE_LIMIT"},
                {"body-limit", "413", "SUMMER_REQUEST_BODY_TOO_LARGE"}}) {
            mvc.perform(get("/api/exception-pilot/" + example[0]).with(jwt()))
                    .andExpect(status().is(Integer.parseInt(example[1])))
                    .andExpect(jsonPath("$.error.code").value(example[2]));
        }
    }

    @RestController
    static class Endpoint {
        @GetMapping("/api/exception-pilot/{mode}")
        Object fail(@PathVariable String mode) {
            switch (mode) {
                case "absent": throw SummerException.withMessage(CommonErrorCode.NOT_FOUND, null, "item");
                case "empty": throw SummerException.withMessage(CommonErrorCode.NOT_FOUND, "");
                case "blank": throw SummerException.withMessage(CommonErrorCode.NOT_FOUND, "   ");
                case "fallback": throw new SummerException(CommonErrorCode.CONFLICT);
                case "unknown": throw new IllegalStateException("private diagnostic");
                case "body-limit": throw new SummerException(com.abluepoint.summer.mvc.error.MvcErrorCode.REQUEST_BODY_TOO_LARGE);
                case "literal": break;
                default: throw new SummerException(com.abluepoint.summer.sample.error.SampleTransportErrorCode.valueOf(mode));
            }
            throw SummerException.withMessage(CommonErrorCode.NOT_FOUND, "  literal {0}  ",
                    new IllegalStateException("private diagnostic"), "item");
        }
    }
    @TestConfiguration
    static class Messages {
        @Bean com.abluepoint.summer.sample.testsupport.ExactCurrentHttpTestFixture.Owner currentTestOwner() {
            return new com.abluepoint.summer.sample.testsupport.ExactCurrentHttpTestFixture.Owner(Endpoint.class);
        }
        @Bean StaticMessageSource messageSource() {
            var source = new StaticMessageSource();
            source.addMessage("SUMMER_NOT_FOUND", Locale.ENGLISH, "Missing {0}");
            source.addMessage("SUMMER_NOT_FOUND", Locale.FRENCH, "Absent {0}");
            return source;
        }
    }
}
