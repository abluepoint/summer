package com.abluepoint.summer.sample;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.Set;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithArchitectureTest {

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(SummerSampleApplication.class).verify();
    }

    @Test
    void expectedSampleModulesAreRegistered() {
        var modules = ApplicationModules.of(SummerSampleApplication.class);

        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .contains("systemparameter");
    }

    @Test
    void sampleConsumesSecurityContractsInsteadOfRuntimeImplementations() {
        Set<String> runtimeTypes = Set.of(
                "BearerOrSessionSecurityContextRepository", "BearerSessionIsolationFilter",
                "DefaultSummerSessionStore", "DefaultSummerIdentityStore", "DefaultSummerOAuthClientStore",
                "DefaultSummerAuthorizationStore", "DefaultSummerConsentStore", "DefaultSummerKeyStore",
                "JsonAuthenticationEntryPoint", "JsonAccessDeniedHandler", "SecurityRequestIds",
                "MethodSecurityExceptionHandler", "SummerCsrfEndpoint");
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample");
        var dependencies = classes.stream()
                .filter(type -> type.getSource().map(source -> !source.getUri().toString().contains("/test/")).orElse(true))
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith("com.abluepoint.summer.security"))
                .filter(dependency -> runtimeTypes.contains(dependency.getTargetClass().getSimpleName()))
                .map(Object::toString).sorted().toList();
        assertThat(dependencies).isEmpty();
    }

}
