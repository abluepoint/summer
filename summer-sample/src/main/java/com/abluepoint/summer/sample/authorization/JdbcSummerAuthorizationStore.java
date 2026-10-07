package com.abluepoint.summer.sample.authorization;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

class JdbcSummerAuthorizationStore extends JdbcOAuth2AuthorizationService
        implements com.abluepoint.summer.security.autoconfigure.storage.SummerAuthorizationStore {
    JdbcSummerAuthorizationStore(JdbcOperations jdbc, RegisteredClientRepository clients) {
        super(jdbc, clients);
        // The Sample form-login bridge carries claims in these two concrete types.
        // Extend Spring's security mapper explicitly; retain its type allowlist.
        var allowedTypes = tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder();
        for (Class<?> type : java.util.List.of(JwtAuthenticationToken.class, Jwt.class,
                java.util.List.of().getClass(), java.util.List.of("").getClass(),
                Map.of().getClass(), Map.of("", "").getClass())) {
            allowedTypes.allowIfSubType(java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(type.getName()) + "$"));
        }
        var mapper = JsonMapper.builder().addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), allowedTypes))
                .addMixIn(JwtAuthenticationToken.class, LoginMixin.class)
                .addMixIn(Jwt.class, JwtMixin.class).build();
        setAuthorizationRowMapper(new JsonMapperOAuth2AuthorizationRowMapper(clients, mapper));
        setAuthorizationParametersMapper(new JsonMapperOAuth2AuthorizationParametersMapper(mapper));
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class LoginMixin {
        @JsonCreator LoginMixin(@JsonProperty("token") Jwt token,
                @JsonProperty("authorities") Collection<GrantedAuthority> authorities,
                @JsonProperty("name") String name) { }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class JwtMixin {
        @JsonCreator JwtMixin(@JsonProperty("tokenValue") String value,
                @JsonProperty("issuedAt") Instant issuedAt, @JsonProperty("expiresAt") Instant expiresAt,
                @JsonProperty("headers") Map<String, Object> headers,
                @JsonProperty("claims") Map<String, Object> claims) { }
    }
}
