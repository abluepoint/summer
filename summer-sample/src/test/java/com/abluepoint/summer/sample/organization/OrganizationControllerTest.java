package com.abluepoint.summer.sample.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrganizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsOrganizationTreeForTheExplicitEnterpriseContext() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-tree", "ent-acme")
                        .header("X-Request-Id", "org-tree-request")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "org-tree-request"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("org-acme"))
                .andExpect(jsonPath("$.data.items[0].children[0].id").value("org-engineering"))
                .andExpect(jsonPath("$.data.items[0].children[0].memberCount").value(1));
    }

    @Test
    void supportsDepartmentListConditionalRevalidationAndParentFiltering() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                        .queryParam("parentId", "org-acme")
                        .header("X-Request-Id", "org-list-request")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].parentId").value("org-acme"))
                .andReturn();

        String etag = first.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                        .queryParam("parentId", "org-acme")
                        .header("If-None-Match", etag)
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }

    @Test
    void returnsMemberAffiliationsForAnOrganizationWithoutCrossTenantData() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members", "ent-acme", "org-engineering")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.organizationId").value("org-engineering"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].memberId").value("member-alice"))
                .andExpect(jsonPath("$.data.items[0].primary").value(true));
    }

    @Test
    void deniesAnOrganizationReadWhenThePrincipalIsNotAnActiveMemberOfTheExplicitEnterprise() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-tree", "ent-other")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void deniesAnOrganizationReadWhenEnterpriseCapabilityIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-tree", "ent-acme")
                        .with(jwt().jwt(token -> token
                                .subject("admin")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.members.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void createsAnOrganizationUnderAnInTenantParentWithAnIdempotentWrite() throws Exception {
        String payload = "{\"parentId\":\"org-acme\",\"unitType\":\"TEAM\",\"name\":\"Platform\",\"code\":\"PLAT\",\"sortOrder\":30}";
        var request = post("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Idempotency-Key", "org-create-platform")
                .with(enterpriseWriteToken("ent-acme"));
        MvcResult first = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.data.parentId").value("org-acme"))
                .andExpect(jsonPath("$.data.code").value("PLAT"))
                .andReturn();

        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Idempotency-Key", "org-create-platform")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("ETag", first.getResponse().getHeader("ETag")));
    }

    @Test
    void rejectsCrossEnterpriseParentsAndSiblingCodeConflicts() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"org-other\",\"unitType\":\"TEAM\",\"name\":\"Bad\",\"code\":\"BAD\"}")
                        .header("Idempotency-Key", "org-cross-parent")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_ORGANIZATION_CONFLICT"));

        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"org-acme\",\"unitType\":\"TEAM\",\"name\":\"Duplicate\",\"code\":\"ENG\"}")
                        .header("Idempotency-Key", "org-duplicate-code")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_ORGANIZATION_CONFLICT"));
    }

    @Test
    void rejectsCyclesAndDeletionOfOrganizationsWithChildrenOrMembers() throws Exception {
        String current = currentUnitEtag("org-acme");
        mockMvc.perform(patch("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", "org-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"org-engineering\"}")
                        .header("If-Match", current)
                        .header("Idempotency-Key", "org-cycle")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_ORGANIZATION_CONFLICT"));

        mockMvc.perform(delete("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", "org-acme")
                        .header("If-Match", current)
                        .header("Idempotency-Key", "org-delete-root")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_ORGANIZATION_CONFLICT"));

        String engineeringEtag = currentUnitEtag("org-engineering");
        mockMvc.perform(delete("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", "org-engineering")
                        .header("If-Match", engineeringEtag)
                        .header("Idempotency-Key", "org-delete-engineering")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_ORGANIZATION_CONFLICT"));
    }

    @Test
    void rejectsStaleConditionalOrganizationWritesAndRecordsAudit() throws Exception {
        mockMvc.perform(patch("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", "org-operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations & Support\"}")
                        .header("If-Match", "\"stale\"")
                        .header("Idempotency-Key", "org-stale-update")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));

        String etag = currentUnitEtag("org-operations");
        mockMvc.perform(patch("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", "org-operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operations & Support\"}")
                        .header("If-Match", etag)
                        .header("Idempotency-Key", "org-update-operations")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Operations & Support"));

        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}/audit", "ent-acme", "org-operations")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.data[0].action").value("ORGANIZATION_UPDATED"))
                .andExpect(jsonPath("$.data[0].actorSubject").value("admin"))
                .andExpect(jsonPath("$.data[0].requestId").isNotEmpty());
    }

    @Test
    void updatesMemberAssignmentsInBatchWithOnePrimaryOrganizationAndIdempotency() throws Exception {
        String payload = "{\"memberIds\":[\"member-alice\"],\"organizationIds\":[\"org-operations\"],\"primaryOrganizationId\":\"org-operations\"}";
        String etag = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-tree", "ent-acme")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(put("/api/v1/enterprises/{enterpriseId}/org-assignments:batch", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("If-Match", etag)
                        .header("Idempotency-Key", "org-assign-alice")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedMemberIds[0]").value("member-alice"));

        mockMvc.perform(put("/api/v1/enterprises/{enterpriseId}/org-assignments:batch", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("If-Match", etag)
                        .header("Idempotency-Key", "org-assign-alice")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryOrganizationId").value("org-operations"));

        mockMvc.perform(put("/api/v1/enterprises/{enterpriseId}/org-assignments:batch", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"member-alice\"],\"organizationIds\":[\"org-engineering\"],\"primaryOrganizationId\":\"org-engineering\"}")
                        .header("If-Match", etag)
                        .header("Idempotency-Key", "org-assign-alice")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members", "ent-acme", "org-operations")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].primary").value(true));

        String updatedEtag = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-tree", "ent-acme")
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(put("/api/v1/enterprises/{enterpriseId}/org-assignments:batch", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"member-alice\"],\"organizationIds\":[],\"primaryOrganizationId\":null}")
                        .header("If-Match", updatedEtag)
                        .header("Idempotency-Key", "org-remove-alice")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationIds.length()").value(0));
    }

    @Test
    void deletesAnEmptyOrganizationAndReplaysTheDeleteIdempotently() throws Exception {
        String payload = "{\"parentId\":\"org-acme\",\"unitType\":\"TEAM\",\"name\":\"Temporary\",\"code\":\"TEMP\"}";
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/org-units", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("Idempotency-Key", "org-create-temp")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isCreated()).andReturn();
        String unitId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        String etag = created.getResponse().getHeader("ETag");
        mockMvc.perform(delete("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", unitId)
                        .header("If-Match", etag).header("Idempotency-Key", "org-delete-temp")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", unitId)
                        .header("If-Match", etag).header("Idempotency-Key", "org-delete-temp")
                        .with(enterpriseWriteToken("ent-acme")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", unitId)
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isNotFound());
    }

    private String currentUnitEtag(String unitId) throws Exception {
        return mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", "ent-acme", unitId)
                        .with(administratorReadToken("ent-acme")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
    }

    // Full-directory assertions use an explicit organization administrator; member-only reads have a separate boundary suite.
    private static org.springframework.test.web.servlet.request.RequestPostProcessor administratorReadToken(String enterpriseId) {
        return jwt().jwt(token -> token
                        .subject("admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterpriseId, "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of(enterpriseId, List.of("enterprise.organization.read", "enterprise.organization.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterpriseWriteToken(String enterpriseId) {
        return jwt().jwt(token -> token
                        .subject("admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterpriseId, "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of(enterpriseId, List.of(
                                "enterprise.organization.read", "enterprise.organization.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.write"));
    }
}
