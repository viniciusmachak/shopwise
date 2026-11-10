package com.shopwise;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ShopwiseApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml();
    }
}
