package com.abluepoint.summer.sample.key;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        MysqlSummerKeyStore restarted = new MysqlSummerKeyStore(repository, "test-only-runtime-master-key-32-bytes");
        org.assertj.core.api.Assertions.assertThat(restarted.get(new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null).get(0).getKeyID()).isEqualTo(kid);
    }
}
