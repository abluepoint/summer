package com.abluepoint.summer.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityCapabilityBoundaryTest {
    @Test
    void storageContractsAreAvailableWithoutBootAssembly() throws Exception {
        for (String name : java.util.List.of(
                "identity.spi.SummerIdentityStore", "oauth2.spi.SummerOAuthClientStore",
                "oauth2.spi.SummerAuthorizationStore", "oauth2.spi.SummerConsentStore",
                "key.spi.SummerKeyStore", "session.spi.SummerSessionStore")) {
            assertThat(Class.forName("com.abluepoint.summer.security." + name).isInterface()).isTrue();
        }
        assertThat(getClass().getClassLoader().getResource(
                "org/springframework/boot/autoconfigure/AutoConfiguration.class")).isNull();
    }
}
