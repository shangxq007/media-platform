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
 * Current EP23 Owner direction preserves intentional publication contracts without subscribers. */
@SpringBootTest
@ActiveProfiles({"test", "preview"})
class EventConsumerClosureAssemblyTest extends PostgresTestContainerSupport {
    @Autowired ApplicationContext context;
    @Autowired OutboxEventRouter router;
    @Autowired List<OutboxEventCatalog> catalogs;

    @Test void retiredContractsCannotRouteInTheAssembledApplication() {
        var retired = List.of("problematic.data.detected", "cost.reservation.created",
                "cost.reservation.released", "reconciliation.completed", "provider.health.degraded",
                "quota.check.requested", "quota.check.result", "billing.invoice.updated");
        for (String name : retired) {
            assertTrue(catalogs.stream().flatMap(c -> c.types().stream())
                    .noneMatch(t -> t.name().equals(name)), name);
            var rejected = assertThrows(OutboxEventRouter.InvalidEvent.class,
                    () -> router.decode(name, 1, "historical", "historical", "{}"), name);
            assertEquals("UNSUPPORTED_EVENT_VERSION", rejected.code(), name);
        }
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "com.example.platform.billing.domain.InvoiceProjectionUpdatedEvent"));
        // Removing publications must not remove the actual detection/reconciliation services.
        assertEquals(1, context.getBeansOfType(ProblematicDataDetectionService.class).size());
        assertEquals(1, context.getBeansOfType(ReconciliationService.class).size());
    }

    @Test void transferredContractsAreRegisteredOnlyByTheirDefiningDomains() {
        assertOwner("audit.usage.anomaly.detected", "com.example.platform.audit.");
        assertOwner("artifact.created", "com.example.platform.artifact.");
        assertOwner("delivery.completed", "com.example.platform.delivery.");
        assertOwner("delivery.failed", "com.example.platform.delivery.");
        assertOwner("timeline.review.created", "com.example.platform.timeline.");
        assertOwner("timeline.review.approved", "com.example.platform.timeline.");
        assertOwner("timeline.revision.created", "com.example.platform.timeline.");
        assertOwner("marketplace.listing.published", "com.example.platform.marketplace.");
    }

    @Test void realTypedSubscriptionsPreserveIntentionalPublicationOnlyContracts() {
        // Inspect registered Spring adapters, not annotations on classes absent from assembly.
        java.util.Collection<?> registered = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                context.getBean("applicationEventMulticaster"), "getApplicationListeners");
        assertNotNull(registered);
        var listeners = registered.stream()
                .filter(org.springframework.context.event.ApplicationListenerMethodAdapter.class::isInstance)
                .map(org.springframework.context.event.ApplicationListenerMethodAdapter.class::cast).toList();
        for (Class<?> publication : List.of(
                com.example.platform.audit.api.event.UsageAnomalyDetectedEvent.class,
                com.example.platform.timeline.api.event.TimelineReviewCreatedEvent.class,
                com.example.platform.timeline.api.event.TimelineRevisionCreatedEvent.class)) {
            assertTrue(listeners.stream().noneMatch(l -> l.supportsEventType(
                    org.springframework.core.ResolvableType.forClass(publication))), publication.getName());
        }
        for (Class<?> subscribed : List.of(
                com.example.platform.timeline.api.event.TimelineReviewApprovedEvent.class,
                com.example.platform.marketplace.api.event.MarketplaceListingPublishedEvent.class,
                com.example.platform.artifact.api.event.ArtifactCreatedEvent.class)) {
            var ids = listeners.stream().filter(l -> l.supportsEventType(
                    org.springframework.core.ResolvableType.forClass(subscribed)))
                    .map(org.springframework.context.event.ApplicationListenerMethodAdapter::getListenerId).toList();
            assertTrue(ids.stream().anyMatch(id -> id.contains("AuditEventHandler")), subscribed.getName());
            assertTrue(ids.stream().anyMatch(id -> id.contains("NotificationEventHandler")), subscribed.getName());
        }
    }

    @Autowired com.example.platform.billing.app.BillingProjectionService billing;
    @Autowired com.example.platform.billing.infrastructure.SubscriptionJdbcRepository subscriptions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void billingReadProjectionStillReadsOnlyTheScopedPrincipalWithoutPublishing() {
        String id = "ep23-" + java.util.UUID.randomUUID();
        var principal = com.example.platform.shared.commercial.PrincipalRef.tenantScoped(
                "ep23", com.example.platform.shared.commercial.PrincipalType.USER, id);
        java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var contract = new com.example.platform.billing.domain.SubscriptionContract(id, "ep23", id, null,
                now, now.plusSeconds(3600), "ACTIVE", 0, "USD", java.util.Map.of(), java.util.Map.of(),
                com.example.platform.billing.domain.SubscriptionContractRole.BASE, "ep23-product");
        subscriptions.insertContract(contract, principal, now);
        long before = jdbc.queryForObject("select count(*) from outbox_events", Long.class);
        assertEquals("ACTIVE", billing.currentState(principal).contractState());
        assertEquals("ep23-product", billing.currentState(principal).canonicalProductCode());
        assertEquals(id, billing.getContract(principal, id).contractId());
        var foreign = com.example.platform.shared.commercial.PrincipalRef.tenantScoped(
                "foreign", com.example.platform.shared.commercial.PrincipalType.USER, id);
        assertNull(billing.currentState(foreign));
        assertNull(billing.getContract(foreign, id));
        assertEquals(before, jdbc.queryForObject("select count(*) from outbox_events", Long.class));
        assertEquals("ACTIVE", billing.getContract(principal, id).lifecycleState());
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
