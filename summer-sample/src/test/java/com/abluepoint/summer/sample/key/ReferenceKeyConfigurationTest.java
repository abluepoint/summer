package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceKeyConfigurationTest {
    @TempDir Path directory;

    @Test
    void configuredFileReferenceSignsAndVerifiesWithoutAGeneratedFallback() throws Exception {
        var key = new RSAKeyGenerator(2048).generate();
        Files.writeString(directory.resolve("signing.pem"), "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(key.toRSAPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        new ApplicationContextRunner().withUserConfiguration(KeyCapabilityConfiguration.class)
                .withPropertyValues("summer.security.storage.keys=reference",
                        "summer.sample.security.key-reference.source=file",
                        "summer.sample.security.key-reference.name=signing.pem",
                        "summer.sample.security.key-reference.allowed-directory=" + directory,
                        "summer.sample.security.key-reference.kid=deployment-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(SummerKeyStore.class);
                    var token = new NimbusJwtEncoder(context.getBean(SummerKeyStore.class)).encode(
                            JwtEncoderParameters.from(JwtClaimsSet.builder().subject("reference-test").build()));
                    assertThat(token.getHeaders().get("kid")).isEqualTo("deployment-key");
                    assertThat(NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build()
                            .decode(token.getTokenValue()).getSubject()).isEqualTo("reference-test");
                });
    }

    @Test
    void environmentAndKubernetesSecretReferencesUseTheirDeclaredSource() throws Exception {
        var key = new RSAKeyGenerator(2048).generate();
        String pem = pem(key.toRSAPrivateKey().getEncoded());
        var environment = new MockEnvironment().withProperty("SUMMER_TEST_SIGNING_KEY", pem);
        assertThat(ReferenceKeyMaterialResolver.resolve(environment, "environment", "SUMMER_TEST_SIGNING_KEY", "", "env-key")
                .kid()).isEqualTo("env-key");
        Files.writeString(directory.resolve("secret"), pem);
        assertThat(ReferenceKeyMaterialResolver.resolve(new MockEnvironment(), "kubernetes-secret", "secret",
                directory.toString(), "k8s-key").source()).isEqualTo("kubernetes-secret");
        assertThat(ReferenceKeyMaterialResolver.resolve(environment, "kubernetes-secret", "SUMMER_TEST_SIGNING_KEY",
                "", "k8s-env-key").kid()).isEqualTo("k8s-env-key");
    }

    @Test
    void invalidReferencesFailClosedWithStableDiagnostics() {
        var environment = new MockEnvironment().withProperty("summer.bad", "value");
        assertThatThrownBy(() -> ReferenceKeyMaterialResolver.resolve(environment, "environment", "summer.bad", "", "kid"))
                .isInstanceOf(ReferenceKeyMaterialResolver.ReferenceKeyResolutionException.class)
                .hasMessageStartingWith("SUMMER_KEY_REFERENCE_INVALID:");
        assertThatThrownBy(() -> ReferenceKeyMaterialResolver.resolve(environment, "file", "../outside", directory.toString(), "kid"))
                .isInstanceOf(ReferenceKeyMaterialResolver.ReferenceKeyResolutionException.class)
                .hasMessageStartingWith("SUMMER_KEY_REFERENCE_INVALID:");
        assertThatThrownBy(() -> ReferenceKeyMaterialResolver.resolve(environment, "file", "missing", directory.toString(), "kid"))
                .isInstanceOf(ReferenceKeyMaterialResolver.ReferenceKeyResolutionException.class)
                .hasMessageStartingWith("SUMMER_KEY_REFERENCE_UNAVAILABLE:");
    }

    private static String pem(byte[] encoded) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(encoded)
                + "\n-----END PRIVATE KEY-----\n";
    }

}
