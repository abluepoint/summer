package com.abluepoint.summer.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummerUiClientPolicyTest {

    @Test
    void rejectsConfidentialSummerUiRegistration() {
        SummerSecurityProperties properties = new SummerSecurityProperties();
        SummerSecurityProperties.Client client = new SummerSecurityProperties.Client();
        client.setId("summer-ui");
        client.setSecret("test-only-placeholder");
        client.setGrantTypes(List.of("client_credentials"));
        properties.getAuthorizationServer().setClients(List.of(client));

        assertThatThrownBy(() -> new SummerSecurityAutoConfiguration()
                .summerRegisteredClientRepository(properties, PasswordEncoderFactories.createDelegatingPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("summer-ui must be a public client");
    }

    @Test
    void retainsGenericConfidentialClientCredentialsSupport() {
        SummerSecurityProperties properties = new SummerSecurityProperties();
        SummerSecurityProperties.Client client = new SummerSecurityProperties.Client();
        client.setId("service-client");
        client.setSecret("test-only-placeholder");
        client.setGrantTypes(List.of("authorization_code", "client_credentials", "refresh_token"));
        client.setRedirectUris(List.of("http://127.0.0.1:8080/callback"));
        properties.getAuthorizationServer().setClients(List.of(client));

        var registered = new SummerSecurityAutoConfiguration()
                .summerRegisteredClientRepository(properties, PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .findByClientId("service-client");
        assertThat(registered).isNotNull();
        assertThat(registered.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.CLIENT_CREDENTIALS,
                AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(registered.getClientAuthenticationMethods()).containsExactlyInAnyOrder(
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC, ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    void rejectsSecretAndExtraScopeOnPublicSummerUi() {
        SummerSecurityProperties properties = new SummerSecurityProperties();
        SummerSecurityProperties.Client client = new SummerSecurityProperties.Client();
        client.setId("summer-ui");
        client.setPublicClient(true);
        client.setGrantTypes(List.of("authorization_code"));
        client.setRedirectUris(List.of("http://127.0.0.1:5173/oauth2/callback"));
        client.setScopes(List.of("openid", "profile", "customer.read", "customer.write"));
        properties.getAuthorizationServer().setClients(List.of(client));

        client.setSecret("test-only-placeholder");
        assertThatThrownBy(() -> new SummerSecurityAutoConfiguration()
                .summerRegisteredClientRepository(properties, PasswordEncoderFactories.createDelegatingPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not define a client secret");

        client.setSecret(null);
        client.setScopes(List.of("openid", "profile", "customer.read", "customer.write", "offline_access"));
        assertThatThrownBy(() -> new SummerSecurityAutoConfiguration()
                .summerRegisteredClientRepository(properties, PasswordEncoderFactories.createDelegatingPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scopes must be exactly");
    }
}
