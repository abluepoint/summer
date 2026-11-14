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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class OrganizationContextHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "alice", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("tree GET /api/v1/enterprises/{enterpriseId}/org-tree",
                        "detail GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}",
                        "currentContext GET /api/v1/enterprises/{enterpriseId}/context",
                        "context PUT /api/v1/enterprises/{enterpriseId}/context",
                        "create POST /api/v1/enterprises/{enterpriseId}/org-units",
                        "update PATCH /api/v1/enterprises/{enterpriseId}/org-units/{unitId}",
                        "assignBatch PUT /api/v1/enterprises/{enterpriseId}/org-assignments:batch")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "directory-operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.member.MemberController")
                && java.util.Set.of("list GET /api/v1/enterprises/{enterpriseId}/members",
                        "detail GET /api/v1/enterprises/{enterpriseId}/members/{memberId}",
                        "disable POST /api/v1/enterprises/{enterpriseId}/members/{memberId}:disable")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mvc;
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory directory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void readsClearsAndRevalidatesTheSelectedContext() throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-acme/context").with(token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.organizationId").doesNotExist());
        mvc.perform(put("/api/v1/enterprises/ent-acme/context").with(token("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-engineering\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/enterprises/ent-acme/context").with(token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.organizationId").value("org-engineering"));
        mvc.perform(put("/api/v1/enterprises/ent-acme/context").with(token("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-operations\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/enterprises/ent-acme/context").with(token("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":null}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/enterprises/ent-acme/context").with(token("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.organizationId").doesNotExist());
    }

    @Test void rejectsSelectionWithoutAnActivePersistedMembership() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/context").with(token("unknown-subject"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-engineering\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void distinguishesMissingParentFromExplicitNull() throws Exception {
        String etag = mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations").with(token("alice")))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        var edited = mvc.perform(patch("/api/v1/enterprises/ent-acme/org-units/org-operations").with(token("alice"))
                .header("If-Match", etag).header("Idempotency-Key", "keep-parent")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Operations renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentId").value("org-acme")).andReturn();
        mvc.perform(patch("/api/v1/enterprises/ent-acme/org-units/org-operations").with(token("alice"))
                .header("If-Match", edited.getResponse().getHeader("ETag")).header("Idempotency-Key", "clear-parent")
                .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentId").doesNotExist());
    }

    @Test void assignsArbitraryPersistedMembersAndObservesStatusChanges() throws Exception {
        String member = "member-" + java.util.UUID.randomUUID();
        String subject = "subject-" + java.util.UUID.randomUUID();
        directory.provision("ent-acme", member, subject, "New member", "new@example.test");
        managed.grantHandlers(Domain.enterprise("ent-acme"), subject, (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("context PUT /api/v1/enterprises/{enterpriseId}/context",
                        "currentContext GET /api/v1/enterprises/{enterpriseId}/context")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        mvc.perform(get("/api/v1/enterprises/ent-acme/members").param("q", "New member").with(memberToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].memberId").value(member));
        String etag = mvc.perform(get("/api/v1/enterprises/ent-acme/org-tree").with(token("alice")))
                .andReturn().getResponse().getHeader("ETag");
        String body = "{\"memberIds\":[\"" + member + "\"],\"organizationIds\":[\"org-engineering\"],\"primaryOrganizationId\":\"org-engineering\"}";
        mvc.perform(put("/api/v1/enterprises/ent-acme/org-assignments:batch").with(token("alice"))
                .header("If-Match", etag).header("Idempotency-Key", member).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/enterprises/ent-acme/context").with(token(subject))
                .contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-engineering\"}"))
                .andExpect(status().isOk());
        String memberEtag = mvc.perform(get("/api/v1/enterprises/ent-acme/members/" + member).with(memberToken()))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(post("/api/v1/enterprises/ent-acme/members/" + member + ":disable").with(memberToken())
                .header("If-Match", memberEtag).header("Idempotency-Key", member)).andExpect(status().isOk());
        // The old token still has its current role; disabling membership immediately invalidates enterprise access.
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from summer_permission_user_role where domain_key=? and username=?",
                Integer.class, Domain.enterprise("ent-acme").key(), subject)).isGreaterThan(0);
        mvc.perform(get("/api/v1/enterprises/ent-acme/context").with(token(subject)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist()).andExpect(jsonPath("$.data.organizationId").doesNotExist());
        String changed = mvc.perform(get("/api/v1/enterprises/ent-acme/org-tree").with(token("alice")))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(put("/api/v1/enterprises/ent-acme/org-assignments:batch").with(token("alice"))
                .header("If-Match", changed).header("Idempotency-Key", "again-" + member).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and member_id=?", String.class, member)).isEqualTo("DISABLED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from summer_directory_journal where namespace='member.audit' and resource_id=?", Integer.class, member)).isEqualTo(1);
    }

    @Test void rejectsAnExistingForeignParentAndAllowsSameCodeInDifferentTenants() throws Exception {
        String tenant = "ent-" + java.util.UUID.randomUUID();
        jdbc.update("insert into summer_enterprise(enterprise_id,name,code,status,review_status,version,created_at,updated_at) values(?,?,?,'ACTIVE','PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", tenant, "Other", tenant);
        var foreign = Domain.enterprise(tenant);
        managed.activeUser(foreign, environment.getRequiredProperty("summer.security.authorization-server.user.username"));
        managed.install(foreign);
        managed.grantHandlers(foreign, "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("create POST /api/v1/enterprises/{enterpriseId}/org-units")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        String payload = "{\"name\":\"Common team\",\"code\":\"shared-" + java.util.UUID.randomUUID() + "\"}";
        var other = mvc.perform(post("/api/v1/enterprises/" + tenant + "/org-units").with(token("operator", tenant))
                .header("Idempotency-Key", "create").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        String foreignParent = com.jayway.jsonpath.JsonPath.read(other.getResponse().getContentAsString(), "$.data.id");
        mvc.perform(post("/api/v1/enterprises/ent-acme/org-units").with(token("alice"))
                .header("Idempotency-Key", tenant).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/enterprises/ent-acme/org-units").with(token("alice"))
                .header("Idempotency-Key", "foreign-" + tenant).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Invalid\",\"code\":\"invalid\",\"parentId\":\"" + foreignParent + "\"}"))
                .andExpect(status().isConflict());
        String etag = mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations").with(token("alice")))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(patch("/api/v1/enterprises/ent-acme/org-units/org-operations").with(token("alice"))
                .header("If-Match", etag).header("Idempotency-Key", "move-" + tenant).contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + foreignParent + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/enterprises/ent-acme/members").param("page", "-1").with(memberToken()))
                .andExpect(status().isBadRequest());
    }

    @Test void managedRolesGrantOnlyTheDeclaredStableOrganizationPermission() throws Exception {
        String subject = "managed-" + java.util.UUID.randomUUID();
        directory.provision("ent-acme", "member-" + java.util.UUID.randomUUID(), subject, subject, "managed@example.test");
        managed.activeUser(Domain.enterprise("ent-acme"), subject);
        // Raw write scope and a retired capability claim cannot manufacture a current write association.
        var auth = jwt().jwt(j -> j.subject(subject)
                .claim("scope", "enterprise.organization.read enterprise.organization.write")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.organization.read", "enterprise.organization.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"), new SimpleGrantedAuthority("SCOPE_enterprise.organization.write"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-tree").with(auth)).andExpect(status().isForbidden());
        String role = managed.grantHandlers(Domain.enterprise("ent-acme"), subject, (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && handler.getMethod().getName().equals("tree") && method.equals("GET")
                && path.equals("/api/v1/enterprises/{enterpriseId}/org-tree"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-tree").with(auth)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/enterprises/ent-acme/org-units").with(auth).header("Idempotency-Key", subject)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied write\",\"code\":\"denied\"}"))
                .andExpect(status().isForbidden());
        managed.revoke(Domain.enterprise("ent-acme"), subject, role);
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-tree").with(auth)).andExpect(status().isForbidden());
    }

    @Test void aNoOpUpdateDoesNotInventAnUnpersistedRevision() throws Exception {
        var before = mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-engineering").with(token("alice")))
                .andExpect(status().isOk()).andReturn();
        String etag = before.getResponse().getHeader("ETag");
        var updated = mvc.perform(patch("/api/v1/enterprises/ent-acme/org-units/org-engineering").with(token("alice"))
                .header("If-Match", etag).header("Idempotency-Key", "no-op").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-engineering").with(token("alice")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", updated.getResponse().getHeader("ETag")));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor memberToken() {
        return jwt().jwt(j -> j.subject("directory-operator").claim("scope", "enterprise.members.read enterprise.members.write").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"), new SimpleGrantedAuthority("SCOPE_enterprise.members.write"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String subject) {
        return token(subject, "ent-acme");
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String subject, String tenant) {
        return jwt().jwt(j -> j.subject(subject).claim("scope", "enterprise.organization.read enterprise.organization.write")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", tenant, "status", "ACTIVE")))
                )
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"), new SimpleGrantedAuthority("SCOPE_enterprise.organization.write"));
    }
}
