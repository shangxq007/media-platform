package com.example.platform.outbox.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded EP29B reintroduction guard; not a production event registry. */
class RetiredEventContractsTest {
    static final List<String> TYPES = List.of("ProblematicDataDetectedEvent", "CostReservationCreatedEvent",
            "CostReservationReleasedEvent", "ReconciliationCompletedEvent", "ProviderHealthDegradedEvent",
            "QuotaCheckRequestedEvent", "QuotaCheckResultEvent");
    static final List<String> KEYS = List.of("problematic.data.detected", "cost.reservation.created",
            "cost.reservation.released", "reconciliation.completed", "provider.health.degraded",
            "quota.check.requested", "quota.check.result");
    static boolean retired(String source) {
        return TYPES.stream().anyMatch(source::contains) || KEYS.stream().anyMatch(source::contains);
    }
    @Test void noProductionDefinitionEmissionOrCodecRegistrationSurvives() throws Exception {
        for (String type : TYPES)
            assertThrows(ClassNotFoundException.class, () -> Class.forName("com.example.platform.shared.events." + type));
        try (var paths = Files.walk(Path.of(".."))) {
            for (var path : paths.filter(p -> p.toString().contains("/src/main/")
                    && (p.toString().endsWith(".java") || p.toString().endsWith(".json") || p.toString().endsWith(".yml"))).toList())
                assertFalse(retired(Files.readString(path)), path.toString());
        }
    }
    @Test void negativeControlsDetectAReintroducedTypeOrRegistration() {
        assertTrue(retired("public record CostReservationCreatedEvent(String id) {}"));
        assertTrue(retired("new OutboxEventType<>(\"provider.health.degraded\", 1, ...);"));
        assertFalse(retired("PROBLEMATIC_DATA_DETECTED"), "Audit action is retained business behavior, not an event registration");
        assertFalse(retired("RuntimeUsageObservedEvent"), "Accepted Usage fact must remain distinct");
    }
}
