package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(
    properties =
        "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class LegacyDataScopeWriteErrorHttpTest {
  private static final String BASE = "/api/v1/enterprises/ent-acme/data-scopes";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @MockitoSpyBean DataScopeService service;

  private RequestPostProcessor actor() {
    return jwt()
        .jwt(
            token ->
                token
                    .subject("summer-user")
                    .claim("scope", "enterprise.data-scope.read enterprise.data-scope.write")
                    .claim(
                        "enterprise_memberships",
                        List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
  }

  @Test
  void managedLegacyPolicyPutRetainsClassified400AndDoesNotChangeLegacyRows() throws Exception {
    rejectLegacyWrite("/system-parameters");
  }

  @Test
  void managedLegacyDirectBindingRetainsClassified400AndDoesNotChangeLegacyRows() throws Exception {
    rejectLegacyWrite("/system-parameters/bindings/USER/summer-user");
  }

  private void rejectLegacyWrite(String path) throws Exception {
    var before = legacyRows();
    var result =
        mvc.perform(
                put(BASE + path)
                    .with(actor())
                    .header("X-Request-Id", "legacy-data-rejected09")
                    .contentType("application/json")
                    .content("{\"operator\":\"AND\",\"rules\":[{\"type\":\"SELF\"}]}"))
            .andReturn();
    assertEquals(
        before,
        legacyRows(),
        "Rejected managed writes must not mutate old policy/binding/audit rows");
    status().isBadRequest().match(result);
    jsonPath("$.error.code").value("SAMPLE_PERMISSION_INVALID").match(result);
    jsonPath("$.requestId").value("legacy-data-rejected09").match(result);
    header().string("X-Request-Id", "legacy-data-rejected09").match(result);
  }

  @Test
  void classifiedDependencyFailureRetainsGlobal503InsteadOfLegacyInternal500() throws Exception {
    failListWith(
        SummerException.withMessage(
            SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
            "Independent classified dependency probe"));
    mvc.perform(get(BASE).with(actor()).header("X-Request-Id", "legacy-data-dependency09"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.error.code").value(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE.code()))
        .andExpect(jsonPath("$.requestId").value("legacy-data-dependency09"));
  }

  @Test
  void originalLegacyExceptionMappingsKeepTheirStatusCodeAndMessage() throws Exception {
    record Case(RuntimeException failure, int status, String code, String message) {}
    for (var example :
        List.of(
            new Case(new org.springframework.security.access.AccessDeniedException("Denied probe"),
                403, "SUMMER_FORBIDDEN", "Denied probe"),
            new Case(new IllegalArgumentException("Invalid probe"),
                400, "SUMMER_DATA_SCOPE_INVALID", "Invalid probe"),
            new Case(new java.util.NoSuchElementException("Missing probe"),
                404, "SUMMER_NOT_FOUND", "Missing probe"),
            new Case(new IllegalStateException("Internal detail must remain private"),
                500, "SUMMER_INTERNAL_ERROR", "An unexpected error occurred"))) {
      failListWith(example.failure());
      mvc.perform(get(BASE).with(actor()).header("X-Request-Id", "legacy-data-protocol09"))
          .andExpect(status().is(example.status()))
          .andExpect(jsonPath("$.error.code").value(example.code()))
          .andExpect(jsonPath("$.error.message").value(example.message()))
          .andExpect(jsonPath("$.requestId").value("legacy-data-protocol09"))
          .andExpect(header().string("X-Request-Id", "legacy-data-protocol09"));
    }
  }

  private void failListWith(RuntimeException failure) {
    DataScopeService target = AopTestUtils.getUltimateTargetObject(service);
    org.mockito.Mockito.reset(target);
    var probe = org.mockito.Mockito.doThrow(failure).when(target);
    // Select the package-private service seam on the spy; the request still uses the actual MVC route.
    ReflectionTestUtils.invokeMethod(probe, "list", eq("ent-acme"), any(Authentication.class));
  }

  private Map<String, List<List<String>>> legacyRows() {
    var snapshot = new LinkedHashMap<String, List<List<String>>>();
    for (String table :
        List.of(
            "summer_data_scope_policy",
            "summer_data_scope_binding",
            "summer_data_scope_policy_audit")) {
      var rows =
          db.query(
              "select * from " + table,
              (rs, number) -> {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++)
                  row.add(rs.getString(column));
                return row;
              });
      rows.sort(java.util.Comparator.comparing(Object::toString));
      snapshot.put(table, rows);
    }
    return snapshot;
  }
}
