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
        Violations filtered = violations
                .filter(it -> !it.toString().contains("Module 'render' depends on module 'extension'"))
                .filter(it -> !it.toString().contains("Module 'compatibility"))
                .filter(it -> !it.toString().contains("Module 'audit' depends on module 'entitlement'"))
                .filter(it -> !it.toString().contains("Module 'audit' depends on module 'billing'"))
                .filter(it -> !it.toString().contains("Module 'observability' depends on module 'audit'"))
                .filter(it -> !it.toString().contains("Module 'entitlement' depends on module 'billing'"))
                .filter(it -> !it.toString().contains("Module 'render' depends on module 'billing'"))
                .filter(it -> !it.toString().contains("Module 'render' depends on module 'entitlement'"))
                .filter(it -> !it.toString().contains("Module 'render' depends on module 'audit'"))
                .filter(it -> !it.toString().contains("Module 'app' depends on non-exposed type com.example.platform.identity"))
                .filter(it -> !it.toString().contains("Module 'security' depends on non-exposed type com.example.platform.identity"))
                .filter(it -> !it.toString().contains("Module 'web' depends on non-exposed type com.example.platform.render"))
                .filter(it -> !it.toString().contains("Module 'web' depends on non-exposed type com.example.platform.prompt"))
                .filter(it -> !it.toString().contains("Module 'web' depends on non-exposed type com.example.platform.identity"));
        assertFalse(filtered.hasViolations(),
                "Module structure violations found: " + filtered.getMessages());
    }
}
