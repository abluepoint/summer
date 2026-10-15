package com.abluepoint.summer.sample.operations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.tracing.sampling.probability=1"})
@AutoConfigureMockMvc
class RuntimeObservabilityHttpTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired io.micrometer.core.instrument.MeterRegistry registry;

    @Test
    void diagnosticsExposeMetricMeaningAndConnectedAuditInsteadOfOnlyMeterCount() throws Exception {
        mvc.perform(get("/internal/v1/diagnostics/runtime").with(jwt().jwt(token -> token.claim("active_enterprise_id", "ent-acme")
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.http.window").value("PROCESS_LIFETIME"))
                .andExpect(jsonPath("$.data.metrics.http.requests").isNumber())
                .andExpect(jsonPath("$.data.metrics.audit.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.metrics.audit.values.success").isNumber())
                .andExpect(jsonPath("$.data.metrics.cache.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.metrics.realtime.status").value("AVAILABLE"));
    }

    @Test
    void prometheusIsRawTextForTheDiagnosticsCapability() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(jwt().jwt(token -> token.claim("active_enterprise_id", "ent-acme")
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("summer_audit_events")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"success\":"))));
    }

    @Test
    void prometheusAndMetricCatalogRejectAnAuthenticatedReaderWithoutDiagnosticsAuthority() throws Exception {
        for (String path : new String[]{"/actuator/prometheus", "/actuator/metrics"}) {
            mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        }
    }

    @Test
    void monitoringRejectsAnonymousRequestsAndDoesNotEchoAnInvalidTraceParent() throws Exception {
        mvc.perform(get("/actuator/prometheus").header("traceparent", "invalid-client-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.matchesPattern("[a-f0-9]{32}")))
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));
    }

    @Test
    void currentNameIdAndActionDimensionsAreRuntimeInfrastructureMetadata() {
        var sources = new java.util.TreeMap<String, Integer>();
        for (var meter : registry.getMeters()) {
            for (var tag : meter.getId().getTags()) {
                if (!java.util.Set.of("name", "id", "action").contains(tag.getKey())) continue;
                String name = meter.getId().getName();
                org.assertj.core.api.Assertions.assertThat(name).matches("(jvm|executor|cache|spring\\.security|hikaricp|jdbc|resilience4j)\\..*");
                org.assertj.core.api.Assertions.assertThat(tag.getValue().contains("sensitive-")
                        || tag.getValue().contains("trace-conflict-") || tag.getValue().contains("/api/")).isFalse();
                sources.merge(name + ":" + tag.getKey(), 1, Integer::sum);
            }
        }
        org.assertj.core.api.Assertions.assertThat(sources).isNotEmpty();
        // Keys/series counts only; raw tag values and request identifiers are not printed.
        System.out.println("T33_TAG_SOURCES " + new tools.jackson.databind.ObjectMapper().writeValueAsString(sources));
    }

    @Test
    void anActualOtelSpanCorrelatesConflictResponseSafeLogAndAppendOnlyAudit() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String requestId = "trace-conflict-" + suffix;
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        String canary = "sensitive-" + suffix;
        String body = "{\"username\":\"trace." + suffix + "\",\"email\":\"" + suffix
                + "@example.test\",\"displayName\":\"" + canary + "\"}";
        var writer = jwt().authorities(new SimpleGrantedAuthority("SCOPE_customer.write"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/customers")
                .with(writer).contentType("application/json").content(body)).andExpect(status().isOk());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.abluepoint.summer.mvc.requestlog");
        var previousLevel = logger.getLevel();
        var lines = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var appender = new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                if (event.getFormattedMessage().contains(requestId)) lines.add(event.getFormattedMessage());
            }
        };
        appender.start(); logger.addAppender(appender); logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/customers")
                            .with(writer).contentType("application/json").content(body)
                            .header("X-Request-Id", requestId)
                            .header("traceparent", "00-" + traceId + "-0123456789abcdef-01"))
                    .andExpect(status().isConflict())
                    .andExpect(header().string("X-Trace-Id", traceId))
                    .andExpect(jsonPath("$.requestId").value(requestId));
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                    "select trace_id from summer_audit_event where request_id=? and result='FAILURE'",
                    String.class, requestId)).isEqualTo(traceId);
            org.assertj.core.api.Assertions.assertThat(lines.stream().anyMatch(line -> line.contains("traceId=" + traceId)
                    && line.contains("status=409"))).isTrue();
            org.assertj.core.api.Assertions.assertThat(lines.stream().noneMatch(line -> line.contains(canary))).isTrue();
        } finally {
            logger.detachAppender(appender); appender.stop(); logger.setLevel(previousLevel);
        }
    }
}
