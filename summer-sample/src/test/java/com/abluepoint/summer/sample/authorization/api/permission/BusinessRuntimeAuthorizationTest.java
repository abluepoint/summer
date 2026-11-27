package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abluepoint.summer.sample.resource.BusinessResourceAccess;
import com.abluepoint.summer.sample.resource.RuntimeBusinessResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, BusinessRuntimeAuthorizationTest.Installed.class})
@org.springframework.test.annotation.DirtiesContext
class BusinessRuntimeAuthorizationTest {
  @Autowired BusinessResourceAccess access;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("runtimeFixture") RuntimeBusinessResources operation;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.context.ConfigurableApplicationContext context;

  @TestConfiguration static class Installed {
    @Bean RuntimeBusinessResources runtimeFixture() {
      return () -> List.of(new RuntimeBusinessResources.Operation("inspect", "MESSAGE", "/fixture10/runtime",
          "PLATFORM", Map.of("frame", List.of("inspect"))));
    }
  }

  @Test void persistedPublicAssociationAllowsAnonymousAndRemovalRevokesNextInvocation() throws Exception {
    assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, operation, "inspect"));
    // Discovery is the formal directory, and association is the formal versioned mutation API.
    String resource = discovered("/fixture10/runtime");
    assertFalse(resource.isBlank());
    String group = db.queryForObject("select group_id from summer_permission where domain_key='PLATFORM:platform' and code='PUBLIC'", String.class);
    var created = mvc.perform(post("/api/v1/platform/permissions").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("code", UUID.randomUUID().toString(), "name", "Runtime10",
            "groupId", group, "type", "INTERFACE", "accessMode", "PUBLIC"))))
        .andExpect(status().isCreated()).andReturn();
    var permission = json.readTree(created.getResponse().getContentAsString()).path("data");
    String id = permission.path("permissionId").asText();
    var collection = mvc.perform(get("/api/v1/platform/permissions/" + id + "/interfaces")
        .with(user("summer-user"))).andExpect(status().isOk()).andReturn();
    String match = collection.getResponse().getHeader("ETag");
    var bound = mvc.perform(put("/api/v1/platform/permissions/" + id + "/interfaces")
        .with(user("summer-user")).with(csrf()).header("If-Match", match)
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("resourceIds", List.of(resource)))))
        .andExpect(status().isOk()).andReturn();
    assertTrue(access.requireRuntime(Domain.platform(), null, operation, "inspect"));
    match = bound.getResponse().getHeader("ETag");
    mvc.perform(put("/api/v1/platform/permissions/" + id + "/interfaces")
        .with(user("summer-user")).with(csrf()).header("If-Match", match)
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content("{\"resourceIds\":[]}")).andExpect(status().isOk());
    assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, operation, "inspect"));
  }

  @Test void copiedMetadataUnknownOperationAndWrongDomainCannotResolveAnInstalledIdentity() {
    RuntimeBusinessResources copied = () -> operation.operations();
    assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, copied, "inspect"));
    assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, operation, "unknown"));
    assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.enterprise("platform"), null, operation, "inspect"));
  }

  @Test void missingAndUnknownModesNeverSelectLegacy() {
    assertThrows(AccessDeniedException.class, () -> access.require(Domain.enterprise("unknown10"), null));
  }

  static class Replacement implements RuntimeBusinessResources {
    @Override public List<Operation> operations() {
      return List.of(new Operation("inspect", "MESSAGE", "/fixture10/replacement", "PLATFORM",
          Map.of("frame", List.of("inspect"))));
    }
  }

  @Test void replacingInstalledOwnerWithSameClassCannotInheritAnOldAssociation() throws Exception {
    var beans = (org.springframework.beans.factory.support.DefaultListableBeanFactory) context.getBeanFactory();
    var first = new Replacement();
    var second = new Replacement();
    beans.registerSingleton("runtimeOwnerA10", first);
    try {
      assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, first, "inspect"));
      String resource = discovered("/fixture10/replacement");
      grant(resource);
      assertTrue(access.requireRuntime(Domain.platform(), null, first, "inspect"));
      assertEquals(resource, discovered("/fixture10/replacement"));
      beans.destroySingleton("runtimeOwnerA10");
      beans.registerSingleton("runtimeOwnerB10", second);
      assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, second, "inspect"));
      assertThrows(AccessDeniedException.class, () -> access.requireRuntime(Domain.platform(), null, first, "inspect"));
      String replacement = discovered("/fixture10/replacement");
      assertNotEquals(resource, replacement);
      grant(replacement);
      assertTrue(access.requireRuntime(Domain.platform(), null, second, "inspect"));
    } finally {
      beans.destroySingleton("runtimeOwnerA10");
      beans.destroySingleton("runtimeOwnerB10");
    }
  }

  private String discovered(String path) throws Exception {
    for (int page = 0; ; page++) {
      var response = mvc.perform(get("/api/v1/platform/authorization/interface-directory")
          .param("size", "200").param("page", Integer.toString(page))
          .with(user("summer-user"))).andExpect(status().isOk()).andReturn();
      var data = json.readTree(response.getResponse().getContentAsString()).path("data");
      for (var item : data.path("items"))
        if (path.equals(item.path("normalizedPath").asText())) return item.path("resourceId").asText();
      if ((page + 1) * 200 >= data.path("total").asInt()) break;
    }
    throw new AssertionError("Installed resource absent from directory");
  }

  private void grant(String resource) throws Exception {
    String group = db.queryForObject("select group_id from summer_permission where domain_key='PLATFORM:platform' and code='PUBLIC'", String.class);
    var response = mvc.perform(post("/api/v1/platform/permissions").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("code", UUID.randomUUID().toString(), "name", "Runtime replacement10",
            "groupId", group, "type", "INTERFACE", "accessMode", "PUBLIC"))))
        .andExpect(status().isCreated()).andReturn();
    String id = json.readTree(response.getResponse().getContentAsString()).path("data").path("permissionId").asText();
    var collection = mvc.perform(get("/api/v1/platform/permissions/" + id + "/interfaces")
        .with(user("summer-user"))).andExpect(status().isOk()).andReturn();
    mvc.perform(put("/api/v1/platform/permissions/" + id + "/interfaces").with(user("summer-user")).with(csrf())
        .header("If-Match", collection.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("resourceIds", List.of(resource)))))
        .andExpect(status().isOk());
  }
}
