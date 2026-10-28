package com.abluepoint.summer.sample;

import java.util.Set;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

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
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample");
        var dependencies = classes.stream()
                .filter(type -> type.getSource().map(source -> !source.getUri().toString().contains("/test/")).orElse(true))
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith("com.abluepoint.summer.security"))
                .filter(dependency -> dependency.getTargetClass().getPackageName().contains(".internal"))
                .map(Object::toString).sorted().toList();
        assertThat(dependencies).isEmpty();
    }

    @Test
    void migratedResponsibilitiesOwnFrameworkFreeDomainRules() {
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample");
        for (String responsibility : Set.of("application", "agent", "enterprise", "member", "organization", "invitation")) {
            String domain = "com.abluepoint.summer.sample." + responsibility + ".domain";
            var rules = classes.stream().filter(type -> type.getPackageName().startsWith(domain)).toList();
            assertThat(rules).as("%s domain rules exist", responsibility).isNotEmpty();
            var violations = rules.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                    .filter(dependency -> {
                        String target = dependency.getTargetClass().getPackageName();
                        return target.startsWith("org.springframework") || target.startsWith("jakarta")
                                || target.startsWith("com.abluepoint.summer.sample." + responsibility + ".api")
                                || target.startsWith("com.abluepoint.summer.sample." + responsibility + ".application")
                                || target.startsWith("com.abluepoint.summer.sample." + responsibility + ".infrastructure");
                    }).map(Object::toString).toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void connectionLifecycleDomainsRemainIndependentOfFrameworkAndAdapters() {
        var modules = Set.of("connection", "delegation", "mcpauthorization", "physicalconnection", "auditledger", "task");
        var dependencies = new ClassFileImporter().importPackages("com.abluepoint.summer.sample").stream()
                .filter(type -> modules.stream().anyMatch(module -> type.getPackageName()
                        .startsWith("com.abluepoint.summer.sample." + module + ".domain")))
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String target = dependency.getTargetClass().getPackageName();
                    return target.startsWith("jakarta.persistence") || target.startsWith("jakarta.servlet")
                            || target.startsWith("org.springframework.data") || target.startsWith("org.springframework.web")
                            || target.startsWith("org.springframework.security")
                            || target.contains(".application") || target.contains(".infrastructure") || target.contains(".api");
                })
                .map(Object::toString).sorted().toList();
        assertThat(dependencies).isEmpty();
    }

    @Test
    void authorizationDomainIsIndependentOfFrameworksAndOuterLayers() {
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample.authorization.domain");
        assertThat(classes).isNotEmpty();
        var forbidden = classes.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String target = dependency.getTargetClass().getPackageName();
                    return target.startsWith("org.springframework") || target.startsWith("jakarta.persistence")
                            || target.startsWith("com.abluepoint.summer.sample.authorization.api")
                            || target.startsWith("com.abluepoint.summer.sample.authorization.application")
                            || target.startsWith("com.abluepoint.summer.sample.authorization.infrastructure");
                }).map(Object::toString).toList();
        assertThat(forbidden).isEmpty();
    }

    @Test
    void menuDomainIsIndependentOfFrameworksAndOuterLayers() {
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample.menu.domain");
        assertThat(classes).isNotEmpty();
        var forbidden = classes.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String target = dependency.getTargetClass().getPackageName();
                    return target.startsWith("org.springframework") || target.startsWith("jakarta.persistence")
                            || target.startsWith("com.abluepoint.summer.sample.menu.api")
                            || target.startsWith("com.abluepoint.summer.sample.menu.application")
                            || target.startsWith("com.abluepoint.summer.sample.menu.infrastructure");
                }).map(Object::toString).toList();
        assertThat(forbidden).isEmpty();
    }

    @Test
    void configurationDomainIsIndependentOfFrameworksAndOuterLayers() {
        var classes = new ClassFileImporter().importPackages("com.abluepoint.summer.sample.configuration.domain");
        assertThat(classes).isNotEmpty();
        var forbidden = classes.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String target = dependency.getTargetClass().getPackageName();
                    return target.startsWith("org.springframework") || target.startsWith("jakarta.persistence")
                            || target.startsWith("com.abluepoint.summer.sample.configuration.api")
                            || target.startsWith("com.abluepoint.summer.sample.configuration.application")
                            || target.startsWith("com.abluepoint.summer.sample.configuration.infrastructure");
                }).map(Object::toString).toList();
        assertThat(forbidden).isEmpty();
    }

}
