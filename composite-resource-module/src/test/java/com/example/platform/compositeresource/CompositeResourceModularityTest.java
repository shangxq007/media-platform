package com.example.platform.compositeresource;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class CompositeResourceModularityTest {

    @Test
    void pureCompositeResourceModuleHasNoModulithBoundaryViolations() {
        ApplicationModules.of(CompositeResourceModule.class).verify();
    }
}
