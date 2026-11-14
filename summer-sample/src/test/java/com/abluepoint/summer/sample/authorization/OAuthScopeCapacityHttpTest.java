package com.abluepoint.summer.sample.authorization;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.context.SpringBootTest(properties = {
    "summer.security.storage.oauth-client=jdbc",
    "summer.security.storage.consent=jdbc",
    "spring.datasource.url=jdbc:h2:mem:perm09_scope_capacity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class OAuthScopeCapacityHttpTest {
  @org.springframework.beans.factory.annotation.Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @org.springframework.beans.factory.annotation.Autowired tools.jackson.databind.ObjectMapper json;

  @org.springframework.beans.factory.annotation.Autowired CurrentManagedAuthorizationTestFixture managed;

  @org.junit.jupiter.api.BeforeEach
  void schemaMatchesProductionTextScopeColumns() {
    managed.grant(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "capacity-manager", "security.oauth-client.write");
    db.execute("create table if not exists oauth2_registered_client (id varchar(100) primary key, client_id varchar(100) unique not null, client_id_issued_at timestamp(6) not null, client_secret varchar(200), client_secret_expires_at timestamp(6), client_name varchar(200) not null, client_authentication_methods text not null, authorization_grant_types text not null, redirect_uris text, post_logout_redirect_uris text, scopes text not null, client_settings text not null, token_settings text not null)");
    db.execute("create table if not exists oauth2_authorization_consent (registered_client_id varchar(100) not null, principal_name varchar(200) not null, authorities text not null, primary key (registered_client_id, principal_name))");
    db.execute("create table if not exists summer_oauth_client_status (client_id varchar(100) primary key, status varchar(20) not null, version integer not null, updated_at timestamp(6) not null)");
  }

  @org.junit.jupiter.api.Test
  void oauthScopeCapacityAccepts4096ButPreservesTokenAndOtherFieldLimits() throws Exception {
    java.util.List<java.util.List<String>> accepted = java.util.List.of(
        java.util.List.of("a".repeat(1500)),
        java.util.List.of("a".repeat(2000), "b".repeat(2095)));
    for (var scopes : accepted) createScopeCapacityClient(scopes, "https://example.test/callback", 201);
    createScopeCapacityClient(java.util.List.of("a".repeat(4097)), "https://example.test/callback", 400);
    for (String invalid : java.util.List.of("bad,scope", "bad scope", "bad\"scope", "bad\\scope"))
      createScopeCapacityClient(java.util.List.of(invalid), "https://example.test/callback", 400);
    createScopeCapacityClient(java.util.List.of("openid"), "https://example.test/" + "a".repeat(2000), 400);
  }

  private void createScopeCapacityClient(java.util.List<String> scopes, String redirect, int expected) throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/security/oauth-clients")
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(t -> t.subject("capacity-manager").claim("scope", "security.oauth-client.write")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.write")))
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .contentType("application/json")
        .content(json.writeValueAsString(java.util.Map.of("clientId", "capacity-" + java.util.UUID.randomUUID(),
            "clientName", "Capacity fixture", "publicClient", true, "redirectUris", java.util.List.of(redirect), "scopes", scopes))))
        .andExpect(status().is(expected));
  }

}
