package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties="summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext
class DictionaryManagedDataHttpTest {
  private static final String BASE="/api/v1/enterprises/ent-acme";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  private RequestPostProcessor actor() {
    return jwt().jwt(token -> token.subject("summer-user").claim("scope","platform.dictionary.read platform.dictionary.write")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))));
  }

  @Test void dictionaryActionsGuardActualDraftDmlAndRollbackTheWholeBulkEdit() throws Exception {
    bindBusinessInterfaces();
    mvc.perform(post(BASE+"/dictionaries").with(actor()).with(csrf().asHeader()).contentType("application/json")
        .content("{\"code\":\"scope10-denied\",\"name\":\"Denied\"}")).andExpect(status().isForbidden());
    assertEquals(0,db.queryForObject("select count(*) from summer_dictionary_type where code='scope10-denied'",Integer.class));
    data("create","SELF");
    mvc.perform(post(BASE+"/dictionaries").with(actor()).with(csrf().asHeader()).contentType("application/json")
        .content("{\"code\":\"scope10-mine\",\"name\":\"Mine\"}")).andExpect(status().isOk());
    assertEquals("summer-user",db.queryForObject("select owner_id from summer_dictionary_type where code='scope10-mine'",String.class));
    mvc.perform(get(BASE+"/dictionaries/scope10-mine/draft").with(actor())).andExpect(status().isNotFound());
    data("read","SELF");
    mvc.perform(get(BASE+"/dictionaries/scope10-mine/draft").with(actor())).andExpect(status().isOk());
    String update="{\"name\":\"Changed\",\"expectedDraftRevision\":0}";
    mvc.perform(patch(BASE+"/dictionaries/scope10-mine").with(actor()).with(csrf().asHeader()).contentType("application/json").content(update))
        .andExpect(status().isNotFound());
    data("update","SELF");
    mvc.perform(patch(BASE+"/dictionaries/scope10-mine").with(actor()).with(csrf().asHeader()).contentType("application/json").content(update))
        .andExpect(status().isOk());
    mvc.perform(post(BASE+"/dictionaries/scope10-mine/items").with(actor()).with(csrf().asHeader()).contentType("application/json")
        .content("{\"value\":\"first\",\"label\":\"First\",\"sortOrder\":0,\"expectedDraftRevision\":1}"))
        .andExpect(status().isOk());
    String draft=json.writeValueAsString(Map.of("name","Changed","publicReadable",false,"enabled",true,"expectedDraftRevision",2,"items",List.of(),"itemOrder",List.of(),"groups",List.of()));
    mvc.perform(put(BASE+"/dictionaries/scope10-mine/draft").with(actor()).with(csrf().asHeader()).contentType("application/json").content(draft))
        .andExpect(status().isForbidden());
    assertEquals(1,db.queryForObject("select count(*) from summer_dictionary_item where dictionary_code='scope10-mine'",Integer.class));
    assertEquals(2L,db.queryForObject("select draft_revision from summer_dictionary_type where code='scope10-mine'",Long.class));
    data("delete","SELF");
    mvc.perform(put(BASE+"/dictionaries/scope10-mine/draft").with(actor()).with(csrf().asHeader()).contentType("application/json").content(draft))
        .andExpect(status().isOk());
    assertEquals(0,db.queryForObject("select count(*) from summer_dictionary_item where dictionary_code='scope10-mine'",Integer.class));
    assertEquals(3L,db.queryForObject("select draft_revision from summer_dictionary_type where code='scope10-mine'",Long.class));
    db.update("insert into summer_dictionary_type(id,tenant_id,created_by,owner_id,code,name,published,public_readable,enabled,draft_revision,effective_revision) values('scope10-other-type','ent-acme','other','other','scope10-other','Other',false,false,true,0,0)");
    db.update("insert into summer_dictionary_item(id,tenant_id,created_by,owner_id,dictionary_code,item_value,label,sort_order,enabled,description,ever_published) values('scope10-other-item','ent-acme','other','other','scope10-other','one','One',0,true,'',false)");
    mvc.perform(post(BASE+"/dictionaries/scope10-other/publish").with(actor()).with(csrf().asHeader()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("expectedVersion",0,"expectedEffectiveRevision",0,"expectedDraftRevision",0,"idempotencyKey","scope10-hidden-publish"))))
        .andExpect(status().isNotFound());
    assertEquals(0,db.queryForObject("select count(*) from summer_dictionary_version where dictionary_code='scope10-other'",Integer.class));
    assertFalse(db.queryForObject("select published from summer_dictionary_type where code='scope10-other'",Boolean.class));
    mvc.perform(post(BASE+"/dictionaries/scope10-mine/items").with(actor()).with(csrf().asHeader()).contentType("application/json")
        .content("{\"value\":\"published\",\"label\":\"Published\",\"sortOrder\":0,\"expectedDraftRevision\":3}"))
        .andExpect(status().isOk());
    mvc.perform(post(BASE+"/dictionaries/scope10-mine/publish").with(actor()).with(csrf().asHeader()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("expectedVersion",0,"expectedEffectiveRevision",0,"expectedDraftRevision",4,"idempotencyKey","scope10-publish"))))
        .andExpect(status().isOk());
    assertEquals(1,db.queryForObject("select count(*) from summer_dictionary_version where dictionary_code='scope10-mine'",Integer.class));
    assertTrue(db.queryForObject("select ever_published from summer_dictionary_item where dictionary_code='scope10-mine'",Boolean.class));

  }

  private String permission(Map<String, Object> extra) throws Exception {
    var group = mvc.perform(post(BASE + "/permission-groups").with(user("summer-user")).with(csrf().asHeader())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content("{\"name\":\"Configuration10\"}")).andExpect(status().isCreated()).andReturn();
    var body = new java.util.HashMap<String, Object>(extra);
    body.put("code", UUID.randomUUID().toString()); body.put("name", "Configuration10"); body.put("accessMode", "PUBLIC");
    body.put("groupId", json.readTree(group.getResponse().getContentAsString()).path("data").path("groupId").asText());
    var result = mvc.perform(post(BASE + "/permissions").with(user("summer-user")).with(csrf().asHeader())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("data").path("permissionId").asText();
  }

  private String data(String action, String preset) throws Exception {
    return permission(Map.of("type", "DATA", "dataScope", Map.of("dataResourceKey", "dictionaries", "action", action, "scopePreset", preset)));
  }

  private void bindBusinessInterfaces() throws Exception {
    var ids = new java.util.ArrayList<String>();
    for (int page = 0; page < 10; page++) {
      var response = mvc.perform(get(BASE + "/authorization/interface-directory").param("size", "200").param("page", "" + page)
          .with(user("summer-user"))).andExpect(status().isOk()).andReturn();
      var data = json.readTree(response.getResponse().getContentAsString()).path("data");
      for (var row : data.path("items"))
        if (row.path("normalizedPath").asText().startsWith("/api/v1/enterprises/{enterpriseId}/dictionaries")) ids.add(row.path("resourceId").asText());
      if ((page + 1) * 200 >= data.path("total").asInt()) break;
    }
    assertFalse(ids.isEmpty());
    String id = permission(Map.of("type", "INTERFACE"));
    var collection = mvc.perform(get(BASE + "/permissions/" + id + "/interfaces").with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    mvc.perform(put(BASE + "/permissions/" + id + "/interfaces").with(user("summer-user")).with(csrf().asHeader())
        .header("If-Match", collection.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("resourceIds", ids)))).andExpect(status().isOk());
  }
}
