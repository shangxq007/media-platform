package com.example.platform.delivery.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeliveryControllerApplicationBoundaryTest {

    @Test
    void controllersDelegateDeliveryPersistenceToTheApplicationBoundary() throws IOException {
        assertControllerUsesApplicationBoundary("DeliveryController.java");
        assertControllerUsesApplicationBoundary("DeliveryAdminController.java");
    }

    private static void assertControllerUsesApplicationBoundary(String controllerFile) throws IOException {
        Path moduleSource = Path.of("src/main/java/com/example/platform/delivery/api", controllerFile);
        if (!Files.exists(moduleSource)) {
            moduleSource = Path.of("delivery-module/src/main/java/com/example/platform/delivery/api", controllerFile);
        }
        String source = Files.readString(moduleSource);

        assertTrue(source.contains("DeliveryAdministrationService"));
        assertFalse(source.contains("DSLContext"));
        assertFalse(source.contains("dsl."));
        assertFalse(source.contains("org.jooq.Record"));
    }
}
