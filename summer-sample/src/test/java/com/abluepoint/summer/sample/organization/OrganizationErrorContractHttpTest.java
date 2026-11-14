package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:organization-error-contract;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class OrganizationErrorContractHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("tree GET /api/v1/enterprises/{enterpriseId}/org-tree",
                        "detail GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}",
                        "members GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members",
                        "create POST /api/v1/enterprises/{enterpriseId}/org-units",
                        "update PATCH /api/v1/enterprises/{enterpriseId}/org-units/{unitId}",
                        "delete DELETE /api/v1/enterprises/{enterpriseId}/org-units/{unitId}",
                        "assignBatch PUT /api/v1/enterprises/{enterpriseId}/org-assignments:batch")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    private static final String BASE = "/api/v1/enterprises/ent-acme";
    private static final String ASSIGN = "{\"memberIds\":[\"member-alice\"],\"organizationIds\":[\"org-operations\"],\"primaryOrganizationId\":\"org-operations\"}";
    @Autowired MockMvc mvc;

    @Test void staleUpdateDeleteAndBatchKeepTheirDistinct412Messages() throws Exception {
        error(patch(BASE + "/org-units/org-operations").content("{\"name\":\"ignored\"}")
                .header("Idempotency-Key", "stale-update").header("If-Match", "\"stale\""),
                412, "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        error(delete(BASE + "/org-units/org-operations").header("Idempotency-Key", "stale-delete")
                .header("If-Match", "\"stale\""), 412, "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        error(put(BASE + "/org-assignments:batch").content(ASSIGN).header("Idempotency-Key", "stale-batch")
                .header("If-Match", "\"stale\""), 412, "SUMMER_PRECONDITION_FAILED", "The organization revision has changed");
        mvc.perform(get(BASE + "/org-units/org-operations").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Operations"));
    }

    @Test void missingMutationKeysRemain409AtAllFourEntrypoints() throws Exception {
        for (var request : List.of(post(BASE + "/org-units").content("{}"),
                patch(BASE + "/org-units/org-operations").content("{}"),
                delete(BASE + "/org-units/org-operations"), put(BASE + "/org-assignments:batch").content(ASSIGN))) {
            error(request, 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key is required");
        }
    }

    @Test void updateAndBatchValidationKeep400AndLiteralMessages() throws Exception {
        error(post(BASE + "/org-units").content("{}").header("Idempotency-Key", "empty-create"),
                400, "SUMMER_ORGANIZATION_INVALID", "name and code are required");
        error(patch(BASE + "/org-units/org-operations").content("{\"name\":\" \"}")
                .header("Idempotency-Key", "blank-name").header("If-Match", etag("/org-units/org-operations")),
                400, "SUMMER_ORGANIZATION_INVALID", "name must not be blank");
        error(put(BASE + "/org-assignments:batch").content("{\"memberIds\":[],\"organizationIds\":[]}")
                .header("Idempotency-Key", "empty-members").header("If-Match", etag("/org-tree")),
                400, "SUMMER_ORGANIZATION_INVALID", "memberIds are required");
        error(put(BASE + "/org-assignments:batch").content(ASSIGN.replace("member-alice", "member-absent"))
                .header("Idempotency-Key", "absent-member").header("If-Match", etag("/org-tree")),
                409, "SUMMER_ORGANIZATION_CONFLICT", "Member is not active in the enterprise");
    }

    @Test void changedCreateAndUpdateReceiptsCannotMutateTheAcceptedResource() throws Exception {
        String id = create("RECEIPT", "create-receipt");
        error(post(BASE + "/org-units").content(payload("DIFFERENT")).header("Idempotency-Key", "create-receipt"),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation");
        String etag = etag("/org-units/" + id);
        mvc.perform(patch(BASE + "/org-units/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Accepted\"}").header("If-Match", etag).header("Idempotency-Key", "update-receipt"))
                .andExpect(status().isOk());
        error(patch(BASE + "/org-units/" + id).content("{\"name\":\"Rejected\"}")
                .header("If-Match", etag).header("Idempotency-Key", "update-receipt"),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation");
        mvc.perform(get(BASE + "/org-units/" + id).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Accepted"));
    }

    @Test void changedDeleteAndBatchReceiptsPreserveTheSecondResourceAndAcceptedAssignments() throws Exception {
        String first = create("DELETE1", "create-delete1");
        String second = create("DELETE2", "create-delete2");
        mvc.perform(delete(BASE + "/org-units/" + first).with(admin()).header("If-Match", etag("/org-units/" + first))
                        .header("Idempotency-Key", "delete-receipt")).andExpect(status().isNoContent());
        error(delete(BASE + "/org-units/" + second).header("If-Match", etag("/org-units/" + second))
                .header("Idempotency-Key", "delete-receipt"), 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation");
        mvc.perform(get(BASE + "/org-units/" + second).with(admin())).andExpect(status().isOk());
        String revision = etag("/org-tree");
        mvc.perform(put(BASE + "/org-assignments:batch").with(admin()).contentType(MediaType.APPLICATION_JSON).content(ASSIGN)
                        .header("If-Match", revision).header("Idempotency-Key", "batch-receipt")).andExpect(status().isOk());
        error(put(BASE + "/org-assignments:batch").content(ASSIGN.replace("org-operations", "org-engineering"))
                .header("If-Match", revision).header("Idempotency-Key", "batch-receipt"),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation");
        mvc.perform(get(BASE + "/org-units/org-operations/members").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.memberId == 'member-alice')]").isNotEmpty());
    }

    private ResultActions error(MockHttpServletRequestBuilder request, int status, String code, String message) throws Exception {
        return mvc.perform(request.with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "organization-contract").header("Accept-Language", "zh-CN"))
                .andExpect(status().is(status)).andExpect(header().string("X-Request-Id", "organization-contract"))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.violations").isEmpty()).andExpect(jsonPath("$.requestId").value("organization-contract"));
    }
    private String create(String code, String key) throws Exception {
        var result = mvc.perform(post(BASE + "/org-units").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(code)).header("Idempotency-Key", key)).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }
    private String payload(String code) { return "{\"parentId\":\"org-acme\",\"unitType\":\"TEAM\",\"name\":\"" + code + "\",\"code\":\"" + code + "\"}"; }
    private String etag(String path) throws Exception {
        return mvc.perform(get(BASE + path).with(admin())).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    }
    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin").claim("scope", "enterprise.organization.read enterprise.organization.write")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        )
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"), new SimpleGrantedAuthority("SCOPE_enterprise.organization.write"));
    }
}
