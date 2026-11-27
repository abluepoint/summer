package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties="summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme,PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class,DepartmentDataScopeHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext
class PluginManagedBoundaryHttpTest {
  @Autowired HistoricalTenantPermissions historical;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private static final String E="/api/v1/enterprises/ent-acme",P="/api/v1/platform";
  @Test void enterprisePluginPermissionCannotReleaseTheGlobalPlatformCustomerSource() throws Exception {
    grant(P,path -> path.startsWith("/api/v1/platform/plugins"));
    grant(E,path -> path.startsWith("/api/v1/enterprises/{enterpriseId}/plugins"));
    grant(E,path -> path.equals("/public/enterprises/{enterpriseId}/system-parameters/{key}"));
    mvc.perform(post(P+"/plugins").with(user("summer-user")).with(csrf()).contentType("application/json")
        .content("{\"pluginId\":\"customer-insights\",\"version\":\"1.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}"))
        .andExpect(status().isCreated());
    mvc.perform(post(P+"/plugins/customer-insights:enable").with(user("summer-user")).with(csrf())).andExpect(status().isOk());
    var actor=jwt().jwt(j -> j.subject("summer-user").claim("scope","enterprise.plugins.read enterprise.plugins.customer-insights.read customer.read system.parameter.read platform.dictionary.read")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))));
    mvc.perform(get(E+"/plugins/customer-insights/summary").with(actor)).andExpect(status().isForbidden());
    grant(P,path -> path.equals("/public/dictionaries/{code}"));
    mvc.perform(get(E+"/plugins/customer-insights/summary").with(actor)).andExpect(status().isForbidden());
    grant(P,path -> path.equals("/api/customers"));
    mvc.perform(get(E+"/plugins/customer-insights/summary").with(actor)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeCustomerCount").isNumber());
  }
  @Test void closedDelegationAliasesAndApprovalWorkflowUseCurrentInterfaceWithoutBusinessClaims() throws Exception {
    var actor=jwt().jwt(j -> j.subject("summer-user")
        .claim("scope","enterprise.agents.delegations.read enterprise.mcp.approvals.write enterprise.mcp.approvals.approve")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))));
    String list=E+"/agents/agent10/delegations";
    mvc.perform(get(list).with(actor)).andExpect(status().isForbidden());
    grant(E,path -> path.startsWith("/api/v1/enterprises/{e}/") || path.startsWith("/api/v1/mcp/approvals/") || path.equals("/internal/v1/mcp/approvals"));
    mvc.perform(get(list).with(actor)).andExpect(status().isOk());
    var created=mvc.perform(post("/internal/v1/mcp/approvals").with(actor).header("Idempotency-Key",UUID.randomUUID().toString())
        .contentType("application/json").content("{\"enterpriseId\":\"ent-acme\",\"mcpServerId\":\"mcp10\",\"toolId\":\"tool10\",\"requestDigest\":\"digest10\",\"approverPrincipalId\":\"summer-user\"}"))
        .andExpect(status().isCreated()).andReturn();
    String id=json.readTree(created.getResponse().getContentAsString()).path("data").path("approvalId").asText();
    mvc.perform(post("/api/v1/mcp/approvals/"+id+":approve").with(actor).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk());
    mvc.perform(post("/api/v1/mcp/approvals/"+id+":recheck").with(actor)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/mcp/approvals/"+id+"/audit").with(actor)).andExpect(status().isOk());
    mvc.perform(post("/internal/v1/mcp/approvals").with(actor).contentType("application/json")
        .content("{\"enterpriseId\":\"ent-other\"}")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.message").value("Delegation capability denied"));
  }

  @Test void websocketSendAndHttpAckQualificationsRemainIndependentAndUseRawDelegation() throws Exception {
    var actor=jwt().jwt(j -> j.subject("summer-user").claim("scope","enterprise.messages.read enterprise.messages.write")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))));
    var endpoint=E+"/authorization/operations";
    mvc.perform(get(endpoint).with(actor).param("actions","messages.send,messages.ack")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['messages.send']").value(false)).andExpect(jsonPath("$.data.actions['messages.ack']").value(false));
    grant(E,path -> path.equals("/api/v1/enterprises/{enterpriseId}/messages/{messageId}:ack"));
    mvc.perform(get(endpoint).with(actor).param("actions","messages.send,messages.ack")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['messages.send']").value(false)).andExpect(jsonPath("$.data.actions['messages.ack']").value(true));
    String runtime=grant(E,path -> path.equals("/ws/v1/messages"));
    mvc.perform(get(endpoint).with(actor).param("actions","messages.send,messages.ack")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['messages.send']").value(true)).andExpect(jsonPath("$.data.actions['messages.ack']").value(true));
    var writeOnly=jwt().jwt(j -> j.subject("summer-user").claim("scope","enterprise.messages.write")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))));
    mvc.perform(get(endpoint).with(writeOnly).param("actions","messages.send,messages.ack")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['messages.send']").value(false)).andExpect(jsonPath("$.data.actions['messages.ack']").value(true));
    var current=historical.readInterfaces(runtime);
    historical.replace(E+"/permissions/"+runtime+"/interfaces", Map.of("resourceIds", List.of()),
        current.etag());
    mvc.perform(get(endpoint).with(actor).param("actions","messages.send,messages.ack")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['messages.send']").value(false)).andExpect(jsonPath("$.data.actions['messages.ack']").value(true));
  }

  @Test void identityHintsRetainTheCurrentManagementRoleBoundaryWithoutBusinessClaims() throws Exception {
    var admin=jwt().jwt(j -> j.subject("summer-user").claim("scope","enterprise.members.write"))
        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_enterprise.members.write"));
    mvc.perform(post("/api/users").with(admin).with(csrf().asHeader()).contentType("application/json")
        .content("{\"username\":\"operation10-reader\",\"password\":\"test-fixture-user-credential10\",\"role\":\"USER\"}"))
        .andExpect(status().isOk());
    var actor=jwt().jwt(j -> j.subject("operation10-reader").claim("scope","enterprise.members.write"));
    String endpoint=P+"/authorization/operations";
    mvc.perform(get(endpoint).with(actor).param("actions","identity.users.create,identity.users.write")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['identity.users.create']").value(false)).andExpect(jsonPath("$.data.actions['identity.users.write']").value(false));
    // These management interfaces already belong to the trusted bootstrap permission.
    // A current identity still needs its separate current management-role qualification.
    mvc.perform(get(endpoint).with(admin).param("actions","identity.users.create,identity.users.write")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['identity.users.create']").value(true)).andExpect(jsonPath("$.data.actions['identity.users.write']").value(true));
  }

  private String grant(String base,java.util.function.Predicate<String> selected) throws Exception {
    var ids=new ArrayList<String>();
    for(int page=0;page<10;page++) {
      var result=mvc.perform(get("/api/v1/platform/authorization/interface-directory").with(user("summer-user")).param("size","200").param("page",""+page))
          .andExpect(status().isOk()).andReturn();
      var data=json.readTree(result.getResponse().getContentAsString()).path("data");
      for(var row:data.path("items")) if(selected.test(row.path("normalizedPath").asText())) ids.add(row.path("resourceId").asText());
      if((page+1)*200>=data.path("total").asInt())break;
    }
    assertFalse(ids.isEmpty());
    if (base.equals(E)) {
      var group = historical.create(E + "/permission-groups", Map.of("name", "Plugin10"));
      var permission = historical.create(E + "/permissions", Map.of("groupId", group.path("groupId").asText(),
          "code", UUID.randomUUID().toString(), "name", "Plugin10", "type", "INTERFACE", "accessMode", "PUBLIC"));
      String id = permission.path("permissionId").asText();
      historical.replace(E + "/permissions/" + id + "/interfaces", Map.of("resourceIds", ids), "\"0\"");
      return id;
    }
    var group=mvc.perform(post(base+"/permission-groups").with(user("summer-user")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
        .contentType("application/json").content("{\"name\":\"Plugin10\"}")).andExpect(status().isCreated()).andReturn();
    String groupId=json.readTree(group.getResponse().getContentAsString()).path("data").path("groupId").asText();
    var permission=mvc.perform(post(base+"/permissions").with(user("summer-user")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
        .contentType("application/json").content(json.writeValueAsString(Map.of("groupId",groupId,"code",UUID.randomUUID().toString(),"name","Plugin10","type","INTERFACE","accessMode","PUBLIC"))))
        .andExpect(status().isCreated()).andReturn();
    String id=json.readTree(permission.getResponse().getContentAsString()).path("data").path("permissionId").asText();
    var bindings=mvc.perform(get(base+"/permissions/"+id+"/interfaces").with(user("summer-user"))).andExpect(status().isOk()).andReturn();
    mvc.perform(put(base+"/permissions/"+id+"/interfaces").with(user("summer-user")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
        .header("If-Match",bindings.getResponse().getHeader("ETag")).contentType("application/json").content(json.writeValueAsString(Map.of("resourceIds",ids))))
        .andExpect(status().isOk());
    return id;
  }
}
