package com.abluepoint.summer.sample.connection;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

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
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class ConnectionServiceControllerTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.platform(), "platform-operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.connection.ConnectionController")
                && java.util.Set.of("list GET /api/v1/platform/connection-services",
                        "create POST /api/v1/platform/connection-services",
                        "status PATCH /api/v1/platform/connection-services/{serviceId}")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "alice", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.connection.ConnectionController")
                && java.util.Set.of("bindings GET /api/v1/enterprises/{enterpriseId}/mcp-connections",
                        "createBinding POST /api/v1/enterprises/{enterpriseId}/mcp-connections",
                        "binding GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}",
                        "tools GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/tools",
                        "health GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/health")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.platform(), "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.connection.ConnectionController")
                && java.util.Set.of("list GET /api/v1/platform/connection-services",
                        "create POST /api/v1/platform/connection-services",
                        "status PATCH /api/v1/platform/connection-services/{serviceId}")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.connection.ConnectionController")
                && java.util.Set.of("bindings GET /api/v1/enterprises/{enterpriseId}/mcp-connections",
                        "createBinding POST /api/v1/enterprises/{enterpriseId}/mcp-connections",
                        "binding GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}",
                        "tools GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/tools",
                        "health GET /api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/health")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.platform(), "user", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.connection.ConnectionController")
                && java.util.Set.of("list GET /api/v1/platform/connection-services")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mockMvc;

    @Test void nativeAccessDenialsKeepTheirOriginalMessageAndCause() throws Exception {
        String path = "/api/v1/platform/connection-services";
        MvcResult result = mockMvc.perform(get(path).with(jwt()).header("X-Request-Id", "native-denied"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Platform connection-service capability denied"))
                .andExpect(jsonPath("$.requestId").value("native-denied"))
                .andExpect(header().string("X-Request-Id", "native-denied"))
                .andExpect(jsonPath("$.error.cause").doesNotExist()).andReturn();
        var error = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
                result.getResolvedException());
        var cause = org.junit.jupiter.api.Assertions.assertInstanceOf(org.springframework.security.access.AccessDeniedException.class,
                error.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(cause.getMessage(), error.explicitMessage());
    }

    @Test void rejectedConnectionMutationsKeepTheirMessagesAndPreserveTheProjection() throws Exception {
        String services = "/api/v1/platform/connection-services";
        failure(post(services).contentType(MediaType.APPLICATION_JSON).content("{}"), 400,
                "SUMMER_CONNECTION_INVALID", "Idempotency-Key is required");
        failure(post(services).contentType(MediaType.APPLICATION_JSON).content("{}").header("Idempotency-Key", "empty"), 400,
                "SUMMER_CONNECTION_INVALID", "serviceCode, name and permitAudience are required");
        String id = createService("cs-stable");
        String duplicate = "{\"serviceCode\":\"cs-stable\",\"name\":\"Duplicate\",\"permitAudience\":\"urn:duplicate\"}";
        failure(post(services).contentType(MediaType.APPLICATION_JSON).content(duplicate).header("Idempotency-Key", "duplicate"), 409,
                "SUMMER_CONNECTION_CONFLICT", "serviceCode already exists");
        failure(post(services).contentType(MediaType.APPLICATION_JSON).content(duplicate).header("Idempotency-Key", "cs-stable"), 409,
                "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused");
        failure(patch(services + "/missing").queryParam("status", "DISABLED")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "missing"), 409,
                "SUMMER_CONNECTION_CONFLICT", "Connection service not found");
        failure(patch(services + "/" + id).queryParam("status", "DISABLED")
                .header("If-Match", "\"999\"").header("Idempotency-Key", "stale"), 412,
                "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        failure(patch(services + "/" + id).queryParam("status", "INVALID")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "invalid-status"), 400,
                "SUMMER_CONNECTION_INVALID", "Invalid connection service status");
        String bindings = "/api/v1/enterprises/ent-acme/mcp-connections";
        failure(post(bindings).contentType(MediaType.APPLICATION_JSON).content("{}").header("Idempotency-Key", "empty-binding"), 400,
                "SUMMER_CONNECTION_INVALID", "connectionServiceId and externalConnectionId are required");
        failure(post(bindings).contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionServiceId\":\"missing\",\"externalConnectionId\":\"ext\"}")
                .header("Idempotency-Key", "missing-service"), 409, "SUMMER_CONNECTION_CONFLICT", "Connection service is not active");
        String binding = "{\"connectionServiceId\":\"" + id + "\",\"externalConnectionId\":\"ext-stable\"}";
        mockMvc.perform(post(bindings).contentType(MediaType.APPLICATION_JSON).content(binding)
                        .header("Idempotency-Key", "binding-stable").with(enterprise("enterprise.connection.write")))
                .andExpect(status().isCreated());
        failure(post(bindings).contentType(MediaType.APPLICATION_JSON).content(binding)
                .header("Idempotency-Key", "binding-duplicate"), 409, "SUMMER_CONNECTION_CONFLICT", "Connection already exists");
        failure(post(bindings).contentType(MediaType.APPLICATION_JSON).content(binding.replace("ext-stable", "ext-other"))
                .header("Idempotency-Key", "binding-stable"), 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused");
        for (String suffix : List.of("", "/tools", "/health")) {
            failure(get(bindings + "/missing" + suffix), 409, "SUMMER_CONNECTION_CONFLICT", "Connection not found");
        }
        String catalog = "/internal/v1/connection-services/" + id + "/connections/ext-catalog/catalog";
        String valid = "{\"enterpriseId\":\"ent-acme\",\"connectionRevision\":2,\"catalogDigest\":\"digest-2\","
                + "\"mcpClient\":{\"clientId\":\"client\"},\"mcpServer\":{\"name\":\"Server\"}}";
        catalogFailure(catalog, "{\"connectionRevision\":0}", "enterpriseId is required", id);
        catalogFailure(catalog, "{\"enterpriseId\":\"ent-acme\",\"connectionRevision\":0}", "catalog revision, digest and server are required", id);
        catalogFailure(catalog, valid.replace("\"clientId\":\"client\"", "\"clientId\":\"\""), "mcpClient.clientId is required", id);
        catalogFailure(catalog, valid.replace("\"mcpServer\"", "\"tools\":[{\"catalogRevision\":0}],\"mcpServer\""), "toolName and schemaDigest are required", id);
        mockMvc.perform(put(catalog).contentType(MediaType.APPLICATION_JSON).content(valid).with(service(id, true)))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));
        for (String body : List.of(valid.replace("\"connectionRevision\":2", "\"connectionRevision\":1"),
                valid.replace("digest-2", "different"))) {
            mockMvc.perform(put(catalog).contentType(MediaType.APPLICATION_JSON).content(body).with(service(id, true))
                            .header("X-Request-Id", "catalog-conflict"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SUMMER_CONNECTION_CONFLICT"))
                    .andExpect(jsonPath("$.error.message").value(body.contains("different") ? "Catalog revision conflict" : "Stale catalog revision"))
                    .andExpect(jsonPath("$.requestId").value("catalog-conflict"));
        }
        mockMvc.perform(get(bindings + "/ext-stable").with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(get(bindings + "/ext-catalog").with(enterprise("enterprise.mcp-connections.read")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.catalogDigest").value("digest-2"));
        mockMvc.perform(get(services).with(platform("platform.connection-services.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].version").value(1)).andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    private void failure(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                         int status, String code, String message) throws Exception {
        List<String> caps = List.of("platform.connection-services.read", "platform.connection-services.write",
                "enterprise.connection.write", "enterprise.mcp-connections.read");
        mockMvc.perform(request.with(jwt().jwt(t -> t.claim("scope", caps.stream().map(value -> value.replaceFirst("^SCOPE_", "")).collect(java.util.stream.Collectors.joining(" "))).subject("operator")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                )
                        .authorities(caps.stream().map(c -> new SimpleGrantedAuthority("SCOPE_" + c)).toArray(SimpleGrantedAuthority[]::new)))
                        .header("X-Request-Id", "connection-rejected"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.requestId").value("connection-rejected"))
                .andExpect(header().string("X-Request-Id", "connection-rejected"))
                .andExpect(jsonPath("$.error.cause").doesNotExist());
    }

    private void catalogFailure(String path, String body, String message, String serviceId) throws Exception {
        mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body).with(service(serviceId, true))
                        .header("X-Request-Id", "catalog-invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_CONNECTION_INVALID"))
                .andExpect(jsonPath("$.error.message").value(message)).andExpect(jsonPath("$.requestId").value("catalog-invalid"));
    }

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
        mockMvc.perform(get("/api/v1/platform/connection-services").with(jwt().jwt(t -> t.claim("scope", "platform.connection-services.read").subject("alice"))
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
        return jwt().jwt(t -> t.claim("scope", capability).subject("platform-operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor service(String serviceId, boolean trusted) {
        return jwt().jwt(t -> t.claim("scope", "internal.connection.catalog.write").subject("connection-service")
                        .claim("connection_service_id", serviceId).claim("connection_service_code", "trusted")
                        .claim("service_trust_verified", trusted).claim("aud", "urn:aict:service"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.connection.catalog.write"));
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterprise(String capability) {
        return jwt().jwt(t -> t.claim("scope", capability).subject("alice")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        )
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
