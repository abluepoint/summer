package com.abluepoint.summer.sample.authorization.api.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class RetiredAuthorizationEventsHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired AuthorizationChangeEventService events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

  @Test
  void retiredFeaturesHaveNoHandlersCatalogEntriesOrDefaultMenus() throws Exception {
    assertThat(mappings.getHandlerMethods().keySet().stream()
        .flatMap(mapping -> mapping.getPatternValues().stream()))
        .noneMatch(path -> path.contains("/authorization-events") || path.endsWith("/authorization/explanation"));
    assertThat(db.queryForObject("select count(*) from summer_interface_resource where active=true and (normalized_path like '/api/authorization-events%' or normalized_path like '%/authorization/explanation')", Long.class)).isZero();
    for (String domain : List.of("/api/v1/platform", "/api/v1/enterprises/ent-acme")) {
      mvc.perform(get(domain + "/authorization/navigation").with(user("summer-user")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.items[?(@.menuId == 'authorization-events')]").isEmpty())
          .andExpect(jsonPath("$.data.items[?(@.menuId == 'permissions')]").isNotEmpty());
    }
  }

  @Test
  void removedReadsAndRetryReturn404WithoutChangingInternalOutbox() throws Exception {
    String resource = "retired-events-" + UUID.randomUUID();
    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      for (var domain : List.of(Domain.platform(), Domain.enterprise("ent-acme")))
        events.enqueue(domain, "ROLE", resource, "ROLE_CHANGED", 1, resource);
    });
    db.update("update summer_authorization_change_outbox set status='DEAD_LETTER',attempts=3,last_error='fixture' where resource_id=?", resource);
    var before = snapshot(resource);
    assertThat(before).hasSize(2);
    for (String tenant : List.of("ent-acme", "platform", "ent-other", "")) {
      mvc.perform(get("/api/authorization-events").param("enterpriseId", tenant).with(user("summer-user")))
          .andExpect(status().isNotFound());
      for (String eventId : db.queryForList("select event_id from summer_authorization_change_outbox where resource_id=?", String.class, resource))
        mvc.perform(post("/api/authorization-events/" + eventId + "/retry")
            .param("enterpriseId", tenant).with(user("summer-user")).with(csrf()))
            .andExpect(status().isNotFound());
    }
    for (String domain : List.of("/api/v1/platform", "/api/v1/enterprises/ent-acme"))
      mvc.perform(get(domain + "/authorization/explanation").param("subject", "summer-user")
          .param("resourceType", "MENU").param("resourceId", "permissions").with(user("summer-user")))
          .andExpect(status().isNotFound());
    assertThat(snapshot(resource))
        .isEqualTo(before);
  }

  private List<List<String>> snapshot(String resource) {
    return db.query("select * from summer_authorization_change_outbox where resource_id=? order by event_id",
        (rs, index) -> {
          var row = new java.util.ArrayList<String>();
          for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) row.add(rs.getString(column));
          return row;
        }, resource);
  }
}
