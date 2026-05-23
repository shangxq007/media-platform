package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/** Zero-tolerance Modulith boundary check. See {@code docs/modulith-debt-register.md}. */
class ModularityTest {

    @Test
    void modularityViolationsWithinBudget() {
        ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);
        Violations violations = modules.detectViolations();
        int count = violations.getMessages().size();
        if (violations.hasViolations()) {
            System.err.println("Modulith violation messages (" + count + "), first: " + violations.getMessages().stream()
                    .limit(3)
                    .toList());
        }
        assertTrue(
                !violations.hasViolations(),
                "Modulith violations remain (messages=" + count + "): " + violations.getMessages().stream()
                        .limit(20)
                        .toList());
    }
}
