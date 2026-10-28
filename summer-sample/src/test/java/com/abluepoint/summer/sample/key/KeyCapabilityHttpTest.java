package com.abluepoint.summer.sample.key;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import com.abluepoint.summer.security.key.spi.SummerKeyStore;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.util.List;
import java.util.Map;

@SpringBootTest(properties = {
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KeyCapabilityHttpTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired SummerKeyStore keyStore;
    @Autowired KeyMaterialRepository repository;
    @Autowired KeyFailureAudit failureAudit;

    @Test
    void exposesOnlyKeyMetadataAndProtectsItByCapability() throws Exception {
        mockMvc.perform(get("/api/security/keys").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.key.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].kid").isString())
                .andExpect(jsonPath("$.data.items[0].algorithm").value("RS256"))
                .andExpect(jsonPath("$.data.items[0].source").value("mysql"))
                .andExpect(jsonPath("$.data.items[0].privateKey").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].ciphertext").doesNotExist());

        mockMvc.perform(get("/api/security/keys").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void signsAndVerifiesWithThePersistentJwk() throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("test").subject("key-test").build();
        Jwt token = new NimbusJwtEncoder(keyStore).encode(JwtEncoderParameters.from(claims));
        org.assertj.core.api.Assertions.assertThat(jwtDecoder.decode(token.getTokenValue()).getSubject())
                .isEqualTo("key-test");
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").isString())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
        String kid = keyStore.get(new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null).get(0).getKeyID();
        org.assertj.core.api.Assertions.assertThat(repository.findById(kid).orElseThrow().ciphertext())
                .doesNotContain("BEGIN", "PRIVATE KEY");
        MysqlSummerKeyStore restarted = new MysqlSummerKeyStore(repository, null, "test-only-runtime-master-key-32-bytes", 90, 30, failureAudit);
        org.assertj.core.api.Assertions.assertThat(restarted.get(new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null).get(0).getKeyID()).isEqualTo(kid);
    }

    @Test
    void rotatesKeysWithOptimisticConcurrencyAndKeepsOldTokensVerifiable() throws Exception {
        String oldToken = new NimbusJwtEncoder(keyStore)
                .encode(JwtEncoderParameters.from(JwtClaimsSet.builder().subject("before-rotation").build()))
                .getTokenValue();
        String oldKid = repository.findAll().stream().findFirst().orElseThrow().kid();

        MvcResult rotated = mockMvc.perform(post("/api/security/keys:rotate")
                        .contentType("application/json")
                        .content("{\"retentionDays\":30}")
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "key-rotation-1")
                        .with(jwt().jwt(token -> token.subject("key-admin"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.key.rotate"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.data.activeKid").isString())
                .andExpect(jsonPath("$.data.retentionDays").value(30))
                .andReturn();
        String activeKid = JsonPath.read(rotated.getResponse().getContentAsString(), "$.data.activeKid");
        org.assertj.core.api.Assertions.assertThat(activeKid).isNotEqualTo(oldKid);
        mockMvc.perform(post("/api/security/keys:rotate")
                        .contentType("application/json").content("{\"retentionDays\":30}")
                        .header("If-Match", "\"1\"").header("Idempotency-Key", "key-rotation-1")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.key.rotate"))))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.data.activeKid").value(activeKid));
        Jwt newToken = new NimbusJwtEncoder(keyStore).encode(JwtEncoderParameters.from(
                JwtClaimsSet.builder().subject("after-rotation").build()));
        org.assertj.core.api.Assertions.assertThat(newToken.getHeaders().get("kid")).isEqualTo(activeKid);
        org.assertj.core.api.Assertions.assertThat(jwtDecoder.decode(oldToken).getSubject()).isEqualTo("before-rotation");
        mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(2));
        MvcResult metadata = mockMvc.perform(get("/api/security/keys").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.key.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> items = JsonPath.read(metadata.getResponse().getContentAsString(), "$.data.items");
        org.assertj.core.api.Assertions.assertThat(items).filteredOn("kid", oldKid).singleElement()
                .extracting("status").isEqualTo("GRACE");
        org.assertj.core.api.Assertions.assertThat(items).filteredOn("kid", activeKid).singleElement()
                .extracting("status").isEqualTo("ACTIVE");
    }

    @Test
    void rejectsStaleRotationAndUnauthorizedRotation() throws Exception {
        mockMvc.perform(post("/api/security/keys:rotate")
                        .contentType("application/json").content("{\"retentionDays\":30}")
                        .header("If-Match", "\"1\"").header("Idempotency-Key", "key-rotation-denied")
                        .with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        mockMvc.perform(post("/api/security/keys:rotate")
                        .contentType("application/json").content("{\"retentionDays\":30}")
                        .header("If-Match", "\"999\"").header("Idempotency-Key", "key-rotation-stale")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.key.rotate"))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));
    }
}
