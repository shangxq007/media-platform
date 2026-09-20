package com.example.platform.events;

import com.example.platform.audit.app.ProblematicDataDetectionService;
import com.example.platform.billing.app.ReconciliationService;
import com.example.platform.outbox.api.event.OutboxEventCatalog;
import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** EP29B's established retirement must hold in application assembly, not only a fixture router.
 * Does not infer dispositions for events whose EP23 decision text is unavailable. */
@SpringBootTest
@ActiveProfiles({"test", "preview"})
class EventConsumerClosureAssemblyTest extends PostgresTestContainerSupport {
    @Autowired ApplicationContext context;
    @Autowired OutboxEventRouter router;
    @Autowired List<OutboxEventCatalog> catalogs;

    @Test void retiredContractsCannotRouteInTheAssembledApplication() {
        var retired = List.of("problematic.data.detected", "cost.reservation.created",
                "cost.reservation.released", "reconciliation.completed", "provider.health.degraded",
                "quota.check.requested", "quota.check.result");
        for (String name : retired) {
            assertTrue(catalogs.stream().flatMap(c -> c.types().stream())
                    .noneMatch(t -> t.name().equals(name)), name);
            var rejected = assertThrows(OutboxEventRouter.InvalidEvent.class,
                    () -> router.decode(name, 1, "historical", "historical", "{}"), name);
            assertEquals("UNSUPPORTED_EVENT_VERSION", rejected.code(), name);
        }
        // Removing publications must not remove the actual detection/reconciliation services.
        assertEquals(1, context.getBeansOfType(ProblematicDataDetectionService.class).size());
        assertEquals(1, context.getBeansOfType(ReconciliationService.class).size());
    }

    @Test void transferredContractsAreRegisteredOnlyByTheirDefiningDomains() {
        assertOwner("artifact.created", "com.example.platform.artifact.");
        assertOwner("delivery.completed", "com.example.platform.delivery.");
        assertOwner("delivery.failed", "com.example.platform.delivery.");
        assertOwner("timeline.review.created", "com.example.platform.timeline.");
        assertOwner("timeline.review.approved", "com.example.platform.timeline.");
        assertOwner("timeline.revision.created", "com.example.platform.timeline.");
        assertOwner("marketplace.listing.published", "com.example.platform.marketplace.");
    }

    private void assertOwner(String name, String owner) {
        var definingCatalogs = catalogs.stream().filter(c -> c.types().stream()
                .anyMatch(t -> t.name().equals(name))).toList();
        assertEquals(1, definingCatalogs.size(), name);
        var catalog = definingCatalogs.getFirst();
        assertTrue(catalog.getClass().getName().startsWith(owner), name);
        var definitions = catalog.types().stream().filter(t -> t.name().equals(name)).toList();
        assertEquals(1, definitions.size(), name);
        assertTrue(definitions.getFirst().payloadType().getName().startsWith(owner), name);
    }
}
