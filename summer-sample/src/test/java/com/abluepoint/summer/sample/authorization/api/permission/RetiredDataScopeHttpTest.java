package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class RetiredDataScopeHttpTest {
  private static final String BASE = "/api/v1/enterprises/ent-acme/data-scopes";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void removedEndpointsHaveNoHandlersAndCannotReadOrMutateHistoricalPolicies() throws Exception {
    assertFalse(mappings.getHandlerMethods().keySet().stream()
        .flatMap(mapping -> mapping.getPatternValues().stream())
        .anyMatch(path -> path.contains("/data-scopes")));
    var before = legacyRows();
    record Endpoint(HttpMethod method, String suffix) {}
    for (var endpoint : List.of(
        new Endpoint(HttpMethod.GET, ""),
        new Endpoint(HttpMethod.GET, "/system-parameters"),
        new Endpoint(HttpMethod.PUT, "/system-parameters"),
        new Endpoint(HttpMethod.GET, "/system-parameters/audit"),
        new Endpoint(HttpMethod.POST, "/system-parameters:preview"),
        new Endpoint(HttpMethod.GET, "/system-parameters/preview"),
        new Endpoint(HttpMethod.GET, "/system-parameters/bindings"),
        new Endpoint(HttpMethod.PUT, "/system-parameters/bindings/USER/summer-user"))) {
      mvc.perform(request(endpoint.method(), BASE + endpoint.suffix())
              .with(jwt().jwt(token -> token.subject("summer-user")))
              .contentType("application/json").content("{}"))
          .andExpect(status().isNotFound());
    }
    assertEquals(before, legacyRows());
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
