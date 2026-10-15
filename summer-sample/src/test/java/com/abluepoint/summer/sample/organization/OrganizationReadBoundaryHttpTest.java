package com.abluepoint.summer.sample.organization;

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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:organization-read-boundary;MODE=MySQL;DB_CLOSE_DELAY=-1", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrganizationReadBoundaryHttpTest {
    @Autowired MockMvc mvc;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    private JwtRequestPostProcessor reader() {
        return jwt().jwt(j -> j.subject("alice").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.organization.read"))))
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
        var admin = jwt().jwt(j -> j.subject("alice").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.organization.read", "enterprise.organization.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"), new SimpleGrantedAuthority("SCOPE_enterprise.organization.write"));
        String etag = mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations").with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        mvc.perform(patch("/api/v1/enterprises/ent-acme/org-units/org-operations").with(admin)
                .header("If-Match", etag).header("Idempotency-Key", "audit-collision")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"name\":\"Private operations\"}"))
                .andExpect(status().isOk());
        members.provision("ent-acme", "collision-member", "org-operations", "Reader", "reader@example.test");
        var reader = jwt().jwt(j -> j.subject("org-operations").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.organization.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/org-units/org-operations/audit").with(reader))
                .andExpect(status().isNotFound());
    }
}
