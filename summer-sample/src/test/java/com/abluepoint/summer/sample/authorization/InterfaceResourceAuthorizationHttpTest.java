package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(CurrentManagedAuthorizationTestFixture.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InterfaceResourceAuthorizationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired CurrentManagedAuthorizationTestFixture fixture;

    @Test
    void scannedTemplateGrantAppliesToDifferentResourcePathsAndCurrentRevokeWins() throws Exception {
        String path = "/api/customers/{username}";
        fixture.activeUser(Domain.platform(), "resource-reader");
        var actor = member("customer.read");
        mvc.perform(get("/api/customers/missing-one").with(actor)).andExpect(status().isForbidden());
        String grant = fixture.grantHandlers(Domain.platform(), "resource-reader",
            (handler, route, method) -> method.equals("GET") && route.equals(path));
        String resource = db.queryForObject("select resource_id from summer_interface_resource where normalized_path=? and http_method='GET' and active=true", String.class, path);
        assertThat(resource).isNotBlank().isNotEqualTo("GET:" + path);
        mvc.perform(get("/api/customers/missing-one").with(actor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/missing-two").with(actor)).andExpect(status().isNotFound());
        db.update("delete from summer_role_permission where domain_key='PLATFORM:platform' and permission_id=?", grant);
        db.update("update summer_authorization_domain set revision=revision+1 where domain_key='PLATFORM:platform'");
        mvc.perform(get("/api/customers/missing-one").with(actor)).andExpect(status().isForbidden());
    }

    @Test
    void oldAnnotatedCodeAndClaimCannotReplaceCurrentResourceGrant() throws Exception {
        String scope = "platform.interface-resources.read";
        fixture.activeUser(Domain.platform(), "resource-reader");
        var actor = member(scope);
        mvc.perform(get("/api/v1/platform/interface-resources").with(actor)).andExpect(status().isForbidden());
        String grant = fixture.grantHandlers(Domain.platform(), "resource-reader", (handler, path, method) ->
            method.equals("GET") && path.equals("/api/v1/platform/interface-resources"));
        mvc.perform(get("/api/v1/platform/interface-resources").param("size", "100").with(actor))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].normalizedPath").isNotEmpty())
            .andExpect(jsonPath("$.data.items[0].httpMethod").isNotEmpty())
            .andExpect(jsonPath("$.data.items[0].handler").isNotEmpty())
            .andExpect(jsonPath("$.data.items[0].permissionCode").doesNotExist());
        db.update("delete from summer_role_permission where domain_key='PLATFORM:platform' and permission_id=?", grant);
        db.update("update summer_authorization_domain set revision=revision+1 where domain_key='PLATFORM:platform'");
        mvc.perform(get("/api/v1/platform/interface-resources").with(actor)).andExpect(status().isForbidden());
    }

    private RequestPostProcessor member(String scope) {
        return jwt().jwt(token -> token.subject("resource-reader").claim("scope", List.of(scope))
            .claim("platform_capabilities", List.of(scope))
            .claim("enterprise_capabilities", Map.of("ent-acme", List.of(scope))));
    }
}
