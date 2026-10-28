package com.abluepoint.summer.sample.connection;


import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConnectionServiceControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void registersActiveConnectionServiceTrustAndListsOnlyForPlatformOperator() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/platform/connection-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceCode\":\"conn-prod\",\"name\":\"Production Connector\",\"permitAudience\":\"urn:aict:conn-prod\",\"protocolVersion\":\"v1\"}")
                        .header("Idempotency-Key", "cs-create-1").with(platform("platform.connection-services.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serviceId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.permitAudience").value("urn:aict:conn-prod"))
                .andReturn();
        String serviceId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.serviceId");
        mockMvc.perform(get("/api/v1/platform/connection-services").with(platform("platform.connection-services.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].serviceId").value(serviceId));
        mockMvc.perform(get("/api/v1/platform/connection-services").with(jwt().jwt(t -> t.subject("alice"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.connection-services.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogSyncIsAuthenticatedByConnectionServiceTrustAndExcludesSecrets() throws Exception {
        String serviceId = createService("cs-catalog-1");
        String body = "{\"enterpriseId\":\"ent-acme\",\"connectionRevision\":1,\"catalogDigest\":\"sha256:catalog-1\",\"mcpClient\":{\"externalRegistrationRef\":\"reg-1\",\"clientId\":\"mcp-client-1\",\"clientName\":\"Connector\",\"clientVersion\":\"1.0\"},\"mcpServer\":{\"name\":\"CRM MCP\",\"resourceUri\":\"https://mcp.example.test\",\"audience\":\"https://mcp.example.test\",\"metadataVersion\":\"2026-08\"},\"tools\":[{\"externalToolId\":\"tool-1\",\"toolName\":\"search_customers\",\"schemaDigest\":\"sha256:schema-1\",\"catalogRevision\":1,\"riskLevel\":\"LOW\",\"requiredScope\":\"mcp.customer.read\",\"requiredCapabilityCode\":\"mcp.customer.read\"}],\"clientSecret\":\"must-not-store\"}";
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-1/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.catalogDigest").value("sha256:catalog-1"))
                .andExpect(jsonPath("$.data.mcpClient.clientId").value("mcp-client-1"))
                .andExpect(jsonPath("$.data.mcpClient.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.data.tools[0].toolName").value("search_customers"));
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-1/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, true)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-1/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("catalog-1", "catalog-2")).with(service(serviceId, true)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUntrustedServiceAndStaleCatalogRevision() throws Exception {
        String serviceId = createService("cs-catalog-2");
        String body = "{\"enterpriseId\":\"ent-acme\",\"connectionRevision\":2,\"catalogDigest\":\"sha256:catalog-2\",\"mcpClient\":{\"clientId\":\"mcp-client-2\"},\"mcpServer\":{\"name\":\"CRM MCP\",\"resourceUri\":\"https://mcp2.example.test\",\"audience\":\"https://mcp2.example.test\"},\"tools\":[]}";
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-2/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, false)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-2/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, true)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-2/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("catalog-2", "catalog-old").replace("\"connectionRevision\":2", "\"connectionRevision\":1")).with(service(serviceId, true)))
                .andExpect(status().isConflict());
    }

    @Test
    void enterpriseReadersRequireMembershipCapabilityScopeAndSeeProjectedTools() throws Exception {
        String serviceId = createService("cs-catalog-3");
        String body = "{\"enterpriseId\":\"ent-acme\",\"connectionRevision\":1,\"catalogDigest\":\"sha256:catalog-3\",\"mcpClient\":{\"clientId\":\"mcp-client-3\"},\"mcpServer\":{\"name\":\"ERP MCP\",\"resourceUri\":\"https://erp.example.test\",\"audience\":\"https://erp.example.test\"},\"tools\":[{\"externalToolId\":\"tool-3\",\"toolName\":\"read_erp\",\"schemaDigest\":\"sha256:schema-3\",\"catalogRevision\":1}]}";
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-3/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, true)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-connections").with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].externalConnectionId").value("ext-3"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-connections/ext-3/tools").with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].toolName").value("read_erp"));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/mcp-connections").with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesBindingDetailAndHealthForTheBoundEnterprise() throws Exception {
        String serviceId = createService("cs-detail-1");
        String body = "{\"enterpriseId\":\"ent-acme\",\"connectionRevision\":3,\"catalogDigest\":\"sha256:detail-1\",\"mcpClient\":{\"clientId\":\"mcp-detail\"},\"mcpServer\":{\"name\":\"Detail MCP\",\"resourceUri\":\"https://detail.example.test\",\"audience\":\"https://detail.example.test\"},\"tools\":[]}";
        mockMvc.perform(put("/internal/v1/connection-services/" + serviceId + "/connections/ext-detail/catalog")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, true)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-connections/ext-detail")
                        .with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.externalConnectionId").value("ext-detail"))
                .andExpect(jsonPath("$.data.connectionRevision").value(3))
                .andExpect(header().string("ETag", startsWith("\"")));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-connections/ext-detail/health")
                        .with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lastSyncedAt").isNotEmpty());
    }

    @Test
    void enterpriseCanCreatePendingBindingIdempotently() throws Exception {
        String serviceId = createService("cs-create-binding");
        String body = "{\"connectionServiceId\":\"" + serviceId + "\",\"externalConnectionId\":\"ext-created\"}";
        MvcResult first = mockMvc.perform(post("/api/v1/enterprises/ent-acme/mcp-connections")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "binding-create-1")
                        .with(enterprise("enterprise.connection.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.externalConnectionId").value("ext-created"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String id = JsonPath.read(first.getResponse().getContentAsString(), "$.data.externalConnectionId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/mcp-connections")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "binding-create-1")
                        .with(enterprise("enterprise.connection.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.externalConnectionId").value(id));
    }

    private String createService(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/connection-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceCode\":\"" + code + "\",\"name\":\"Connector\",\"permitAudience\":\"urn:aict:" + code + "\",\"protocolVersion\":\"v1\"}")
                        .header("Idempotency-Key", code).with(platform("platform.connection-services.write")))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.serviceId");
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor platform(String capability) {
        return jwt().jwt(t -> t.subject("platform-operator").claim("platform_capabilities", List.of(capability)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor service(String serviceId, boolean trusted) {
        return jwt().jwt(t -> t.subject("connection-service")
                        .claim("connection_service_id", serviceId).claim("connection_service_code", "trusted")
                        .claim("service_trust_verified", trusted).claim("aud", "urn:aict:service"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.connection.catalog.write"));
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterprise(String capability) {
        return jwt().jwt(t -> t.subject("alice")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
