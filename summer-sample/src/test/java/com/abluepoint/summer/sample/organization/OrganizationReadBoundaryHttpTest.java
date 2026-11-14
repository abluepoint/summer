package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:organization-read-boundary;MODE=MySQL;DB_CLOSE_DELAY=0", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class OrganizationReadBoundaryHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "alice", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("tree GET /api/v1/enterprises/{enterpriseId}/org-tree",
                        "list GET /api/v1/enterprises/{enterpriseId}/org-units",
                        "detail GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}",
                        "members GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members",
                        "audit GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/audit")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mvc;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    private JwtRequestPostProcessor reader() {
        return jwt().jwt(j -> j.subject("alice").claim("scope", "enterprise.organization.read").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        )
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"));
    }
    @Test void readerOnlySeesAssignedOrganizationsAcrossEveryDirectoryReadSurface() throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-engineering").with(reader()))
                .andExpect(status().isOk());
        for (String suffix : List.of("", "/members", "/audit")) {
            mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations" + suffix).with(reader()))
                    .andExpect(status().isNotFound());
        }
        for (String path : List.of("org-tree", "org-units")) {
            mvc.perform(get("/api/v1/enterprises/ent-acme/" + path).with(reader()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value("org-engineering"));
        }
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-units").param("parentId", "org-operations").with(reader()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void subjectNameMatchingAnOrganizationIdDoesNotExposeThatOrganizationsAudit() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "alice", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("update PATCH /api/v1/enterprises/{enterpriseId}/org-units/{unitId}")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        var admin = jwt().jwt(j -> j.subject("alice").claim("scope", "enterprise.organization.read enterprise.organization.write").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                )
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"), new SimpleGrantedAuthority("SCOPE_enterprise.organization.write"));
        String etag = mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations").with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        mvc.perform(patch("/api/v1/enterprises/ent-acme/org-units/org-operations").with(admin)
                .header("If-Match", etag).header("Idempotency-Key", "audit-collision")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"name\":\"Private operations\"}"))
                .andExpect(status().isOk());
        members.provision("ent-acme", "collision-member", "org-operations", "Reader", "reader@example.test");
        managed.grantHandlers(Domain.enterprise("ent-acme"), "org-operations", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("audit GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/audit")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        var reader = jwt().jwt(j -> j.subject("org-operations").claim("scope", "enterprise.organization.read").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                )
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations/audit").with(reader))
                .andExpect(status().isNotFound());
    }
}
