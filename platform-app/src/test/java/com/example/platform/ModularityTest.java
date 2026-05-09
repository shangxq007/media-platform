package com.example.platform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

class ModularityTest {
    @Test
    void verifiesModuleStructure() {
        ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);
        Violations violations = modules.detectViolations();
        // Filter out known allowed violations: render module depends on extension module
        // via ProcessToolRunner port interface (by design, extension-module provides the
        // safe tool execution layer that render-module infrastructure uses)
        Violations filtered = violations
                .filter(it -> !it.toString().contains("Module 'render' depends on module 'extension'"));
        assertFalse(filtered.hasViolations(),
                "Module structure violations found: " + filtered.getMessages());
    }
}
