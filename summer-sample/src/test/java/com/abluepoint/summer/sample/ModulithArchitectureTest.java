package com.abluepoint.summer.sample;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(SummerSampleApplication.class).verify();
    }
}
