package com.abluepoint.summer.sample.task;

import java.util.List;
import java.util.Map;

/** Cookie-authenticated request fixture for native EventSource endpoints. */
final class TaskAuthenticationFixture {
    static org.springframework.test.web.servlet.request.RequestPostProcessor cookie(String capability) {
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("task-session-fixture")
                        .header("alg", "none").issuedAt(java.time.Instant.now().minusSeconds(30))
                        .expiresAt(java.time.Instant.now().plusSeconds(300)).subject("task-admin")
                        .claim("scope", capability)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))).build();
        var authentication = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                token, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability)));
        return request -> {
            var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            request.getSession().setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .authentication(authentication).postProcessRequest(request);
        };
    }
}
