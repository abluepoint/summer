package com.abluepoint.summer.sample;

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
                .containsExactlyInAnyOrder("customer", "operations", "shared");
    }
}
