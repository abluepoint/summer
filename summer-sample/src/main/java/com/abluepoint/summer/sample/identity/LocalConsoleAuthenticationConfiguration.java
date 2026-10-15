package com.abluepoint.summer.sample.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Development-only bridge: turns the sample's local form login into the same
 * claim shape used by the bearer-token API. Production deployments must use a
 * real OIDC issuer and must not rely on this in-memory identity.
 */
@Configuration
@AutoConfigureBefore(com.abluepoint.summer.security.autoconfigure.SummerSecurityAutoConfiguration.class)
class LocalConsoleAuthenticationConfiguration {

    private static final String ENTERPRISE_ID = "ent-acme";
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    AuthenticationSuccessHandler localConsoleAuthenticationSuccessHandler(
            SummerSecurityProperties properties,
            SecurityContextRepository securityContextRepository,
            LocalConsoleIdentityClaims identityClaims
    ) {
        SavedRequestAwareAuthenticationSuccessHandler savedRequestHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        savedRequestHandler.setDefaultTargetUrl(defaultWorkspaceUrl(
                properties.getAuthorizationServer().getLoginPage()));
        return (request, response, authentication) -> {
            if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
                savedRequestHandler.onAuthenticationSuccess(request, response, authentication);
                return;
            }
            Instant now = Instant.now();
            Map<String, Object> membership = new LinkedHashMap<>();
            membership.put("enterpriseId", ENTERPRISE_ID);
            membership.put("displayName", "北京AI很厉害科技有限公司");
            membership.put("status", "ACTIVE");

            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("enterprise_memberships", List.of(membership));
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            List<String> enterpriseCapabilities = admin ? capabilities() : List.of();
            List<String> platformCapabilities = admin ? platformCapabilities() : List.of();
            claims.put("enterprise_capabilities", Map.of(ENTERPRISE_ID, enterpriseCapabilities));
            claims.put("platform_capabilities", platformCapabilities);
            Map<String, Object> resolvedClaims = identityClaims.resolve(authentication.getName(), claims);
            Jwt jwt = Jwt.withTokenValue("local-console-session")
                    .header("alg", "none")
                    .subject(authentication.getName())
                    .claim("name", authentication.getName())
                    .claim("principal_type", "USER")
                    .claims(values -> values.putAll(resolvedClaims))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(8 * 60 * 60))
                    .build();

            List<GrantedAuthority> authorities = new ArrayList<>();
            LocalConsoleIdentityClaims.capabilities(resolvedClaims).forEach(value -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + value)));
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            authorities.add(FactorGrantedAuthority.fromFactor("PASSWORD"));
            JwtAuthenticationToken localJwt = new JwtAuthenticationToken(jwt, authorities, authentication.getName());
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(localJwt);
            securityContextRepository.saveContext(context, request, response);
            savedRequestHandler.onAuthenticationSuccess(request, response, localJwt);
        };
    }

    private static String defaultWorkspaceUrl(String loginPage) {
        if (loginPage == null || loginPage.isBlank()) {
            return "/dashboard/workplace";
        }
        try {
            URI uri = URI.create(loginPage);
            if (uri.isAbsolute()) {
                return uri.resolve("/dashboard/workplace").toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to the local path when the configured login page is invalid.
        }
        return "/dashboard/workplace";
    }

    private static List<String> capabilities() {
        return List.of(
                "enterprise.members.read", "enterprise.members.write",
                "enterprise.organization.read", "enterprise.organization.write",
                "enterprise.organizations.read", "enterprise.organizations.write",
                "enterprise.application.read", "enterprise.application.write",
                "enterprise.applications.read", "enterprise.applications.write",
                "enterprise.agents.read", "enterprise.agents.write",
                "enterprise.agents.delegations.read", "enterprise.agents.delegations.write",
                "enterprise.connection.read", "enterprise.connection.write",
                "enterprise.mcp-connections.read",
                "enterprise.audit.read", "enterprise.audit.write",
                "enterprise.policy.read", "enterprise.policy.write",
                "enterprise.policies.read", "enterprise.policies.write",
                "enterprise.role.read", "enterprise.role.write",
                "enterprise.roles.read", "enterprise.roles.write",
                "enterprise.menu.read", "enterprise.menu.write",
                "enterprise.capabilities.read", "enterprise.capabilities.write",
                "enterprise.bundles.read", "enterprise.bundles.write",
                "enterprise.invitation.read", "enterprise.invitation.write",
                "enterprise.join-application.read", "enterprise.join-application.review",
                "enterprise.join-application.submit",
                "enterprise.mcp.approvals.approve", "enterprise.mcp.approvals.write",
                "enterprise.mcp.execution.write",
                "mcp.customer.read", "mcp.customer.write"
        );
    }

    private static List<String> platformCapabilities() {
        return List.of(
                "platform.capabilities.read", "platform.capabilities.write",
                "platform.interface-resources.read", "platform.menu.write",
                "platform.connection-services.read", "platform.connection-services.write",
                "platform.enterprise-review.read", "platform.enterprise-review.write",
                "platform.enterprise-review.approve",
                "platform.enterprise.read", "platform.enterprise.write",
                "capabilities.platform.read", "capabilities.enterprise.read",
                "internal.release.read", "internal.release.write"
        );
    }
}
