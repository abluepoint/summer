package com.abluepoint.summer.sample.resource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "summer.security.authorization-server.enabled=false")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(InterfaceResourceDisabledSecurityHttpTest.ResourceServerOnly.class)
class InterfaceResourceDisabledSecurityHttpTest {
    @Autowired MockMvc mvc;
    @Test void absentAuthorizationServerDoesNotInventProtocolEndpoints() throws Exception {
        var result = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                        .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> rows = JsonPath.read(result.getResponse().getContentAsString(), "$.data.items");
        assertThat(rows.stream().map(row -> row.get("normalizedPath")).toList()).doesNotContain(
                "/oauth2/token", "/oauth2/authorize", "/userinfo", "/connect/logout", "/.well-known/openid-configuration", "/login", "/logout");
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ResourceServerOnly {
        @Bean org.springframework.security.web.context.SecurityContextRepository resourceServerSessionRepository() {
            return new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
        }
        // A separate resource server can validate external tokens without enabling a local issuer.
        @Bean org.springframework.security.oauth2.jwt.JwtDecoder externalIssuerDecoder() {
            return token -> { throw new org.springframework.security.oauth2.jwt.JwtException("No external token fixture supplied"); };
        }
    }
}
