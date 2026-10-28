package com.abluepoint.summer.sample;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityLayerBoundaryTest {
    @Test
    void identityAndBffOwnFrameworkFreeSessionRules() throws Exception {
        Class.forName("com.abluepoint.summer.sample.identity.domain.SessionRecord");
        Class.forName("com.abluepoint.summer.sample.bff.domain.OidcIdentity");
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample.identity.domain", "com.abluepoint.summer.sample.bff.domain");
        assertThat(classes.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dep -> dep.getTargetClass().getPackageName().startsWith("org.springframework")
                        || dep.getTargetClass().getPackageName().startsWith("jakarta.persistence")
                        || dep.getTargetClass().getPackageName().startsWith("jakarta.servlet")
                        || dep.getTargetClass().getPackageName().matches(".*\\.(api|application|infrastructure)(\\..*)?"))
                .map(Object::toString).toList()).isEmpty();
    }
}
