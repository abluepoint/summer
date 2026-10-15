package com.abluepoint.summer.sample.operations;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class RuntimeMigrationDiagnosticsHttpTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean Flyway flyway;
    private MigrationInfoService migrations;

    @BeforeEach
    void validAppliedMigration() {
        migrations = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(migrations);
        when(flyway.validateWithResult()).thenReturn(validation(true));
        when(migrations.pending()).thenReturn(new MigrationInfo[0]);
        when(migrations.applied()).thenReturn(new MigrationInfo[]{mock(MigrationInfo.class)});
    }

    @Test
    void reportsHealthyOnlyWhenAppliedMigrationsValidateWithNothingPending() throws Exception {
        expectMigrationStatus("UP");
    }

    @Test
    void distinguishesPendingMigrationsFromAHealthyDatabase() throws Exception {
        when(migrations.pending()).thenReturn(new MigrationInfo[]{mock(MigrationInfo.class)});
        expectMigrationStatus("PENDING");
    }

    @Test
    void distinguishesFailedMigrationValidationFromAvailability() throws Exception {
        when(flyway.validateWithResult()).thenReturn(validation(false));
        expectMigrationStatus("VALIDATION_FAILED");
    }

    @Test
    void doesNotReportAnEmptyMigrationHistoryAsAppliedSuccessfully() throws Exception {
        when(migrations.applied()).thenReturn(new MigrationInfo[0]);
        expectMigrationStatus("NOT_APPLIED");
    }

    @Test
    void reportsUnavailableWithoutLeakingDriverFailureDetails() throws Exception {
        when(flyway.validateWithResult()).thenThrow(new IllegalStateException("private-runtime-diagnostic-marker"));
        expectMigrationStatus("UNAVAILABLE");
    }

    private void expectMigrationStatus(String expected) throws Exception {
        mockMvc.perform(get("/internal/v1/diagnostics/runtime")
                        .header("X-Request-Id", "migration-diagnostic-request")
                        .with(jwt().jwt(token -> token.subject("operator").claim("active_enterprise_id", "ent-acme")
                                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "migration-diagnostic-request"))
                .andExpect(jsonPath("$.requestId").value("migration-diagnostic-request"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-runtime-diagnostic-marker"))))
                .andExpect(jsonPath("$.data.components[?(@.name == 'migrations')].status").value(contains(expected)));
    }

    private ValidateResult validation(boolean successful) {
        return new ValidateResult("test", "redacted", null, successful, 1, List.of(), List.of());
    }
}
