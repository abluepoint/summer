package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/** The real public controller must establish the same managed resource context as /api. */
@SpringBootTest(
    properties =
        "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme,PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@DirtiesContext
class PublicConfigurationFinalHandlerHttpTest {
  private static final String BASE = "/api/v1/enterprises/ent-acme";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private RequestPostProcessor writer() {
    return jwt()
        .jwt(
            token ->
                token
                    .subject("summer-user")
                    .claim("scope", "platform.dictionary.read platform.dictionary.write")
                    .claim(
                        "enterprise_memberships",
                        List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "SCOPE_platform.dictionary.read"),
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "SCOPE_platform.dictionary.write"));
  }

  @Test
  void explicitlyPublicInterfaceAndDataAllowPublishedDictionaryAtFinalMvcHandler()
      throws Exception {
    var ids = new ArrayList<String>();
    for (int page = 0; page < 20; page++) {
      var response =
          mvc.perform(
                  get(BASE + "/authorization/interface-directory")
                      .param("page", "" + page)
                      .param("size", "200")
                      .with(user("summer-user")))
              .andExpect(status().isOk())
              .andReturn();
      var data = json.readTree(response.getResponse().getContentAsString()).path("data");
      for (var row : data.path("items")) {
        String path = row.path("normalizedPath").asText();
        if (path.startsWith("/api/dictionaries")
            || path.startsWith("/api/v1/enterprises/{enterpriseId}/dictionaries")
            || path.startsWith("/public/dictionaries")
            || path.startsWith("/public/enterprises/{enterpriseId}/dictionaries"))
          ids.add(row.path("resourceId").asText());
      }
      if ((page + 1) * 200 >= data.path("total").asInt()) break;
    }
    assertFalse(ids.isEmpty());
    String permission = permission(Map.of("type", "INTERFACE"));
    bind(permission, ids, "\"0\"");
    for (String action : List.of("create", "update", "read"))
      permission(
          Map.of(
              "type",
              "DATA",
              "dataScope",
              Map.of(
                  "dataResourceKey",
                  "dictionaries",
                  "action",
                  action,
                  "scopePreset",
                  "TENANT_ALL")));

    String route = BASE + "/dictionaries/public-final09";
    mvc.perform(
            post(BASE + "/dictionaries")
                .with(writer())
                .contentType("application/json")
                .content(
                    "{\"code\":\"public-final09\",\"name\":\"Public final"
                        + " handler\",\"publicReadable\":true,\"enabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(
            post(route + "/items")
                .with(writer())
                .contentType("application/json")
                .content(
                    "{\"expectedDraftRevision\":0,\"value\":\"aa\",\"label\":\"Allowed\",\"sortOrder\":1,\"enabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get("/public/enterprises/ent-acme/dictionaries/public-final09"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(route + "/publish")
                .with(writer())
                .contentType("application/json")
                .content(
                    "{\"expectedVersion\":0,\"expectedDraftRevision\":1,\"expectedEffectiveRevision\":0}"))
        .andExpect(status().isOk());

    for (String publicRoute :
        List.of(
            "/public/dictionaries/public-final09",
            "/public/enterprises/ent-acme/dictionaries/public-final09")) {
      mvc.perform(get(publicRoute).with(writer()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.items[0].value").value("aa"));
      mvc.perform(get(publicRoute + "/items/aa").with(writer()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.value").value("aa"));
    }
    mvc.perform(get("/public/enterprises/ent-acme/dictionaries/public-final09"))
        .andExpect(
            request()
                .attribute(
                    com.abluepoint.summer.sample.authorization.application.permission
                        .ManagedConsumerAccess.DOMAIN_ATTRIBUTE,
                    com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain
                        .enterprise("ent-acme")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].value").value("aa"));
    mvc.perform(get("/public/enterprises/ent-acme/dictionaries/public-final09/items/aa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.value").value("aa"));
    mvc.perform(
            get("/public/enterprises/ent-acme/dictionaries/public-final09")
                .header("Authorization", "Bearer invalid-public09"))
        .andExpect(status().isUnauthorized());
    bind(permission, List.of(), "\"1\"");
    mvc.perform(get("/public/enterprises/ent-acme/dictionaries/public-final09"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/public/enterprises/ent-acme/dictionaries/public-final09").with(writer()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/public/dictionaries/public-final09/items/aa").with(writer()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/public/unmapped-final09")).andExpect(status().isNotFound());
  }

  @Test
  void invalidBearerCannotBecomeAnonymousOnPublicConfigurationRoutes() throws Exception {
    for (String route :
        List.of(
            "/public/dictionaries/public-final09",
            "/public/dictionaries/public-final09/items/aa",
            "/public/enterprises/ent-acme/dictionaries/public-final09",
            "/public/enterprises/ent-acme/dictionaries/public-final09/items/aa",
            "/public/system-parameters/public-final09",
            "/public/enterprises/ent-acme/system-parameters/public-final09"))
      mvc.perform(get(route).header("Authorization", "Bearer invalid-public09"))
          .andExpect(status().isUnauthorized());
  }

  private String permission(Map<String, Object> extra) throws Exception {
    var group =
        mvc.perform(
                post(BASE + "/permission-groups")
                    .with(user("summer-user"))
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content("{\"name\":\"Public final handler09\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    var body = new HashMap<String, Object>(extra);
    body.put(
        "groupId",
        json.readTree(group.getResponse().getContentAsString())
            .path("data")
            .path("groupId")
            .asText());
    body.put("code", UUID.randomUUID().toString());
    body.put("name", "Public final handler09");
    body.put("accessMode", "PUBLIC");
    var created =
        mvc.perform(
                post(BASE + "/permissions")
                    .with(user("summer-user"))
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(created.getResponse().getContentAsString())
        .path("data")
        .path("permissionId")
        .asText();
  }

  private void bind(String permission, List<String> ids, String etag) throws Exception {
    mvc.perform(
            put(BASE + "/permissions/" + permission + "/interfaces")
                .with(user("summer-user"))
                .with(csrf())
                .header("If-Match", etag)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("resourceIds", ids))))
        .andExpect(status().isOk());
  }
}
