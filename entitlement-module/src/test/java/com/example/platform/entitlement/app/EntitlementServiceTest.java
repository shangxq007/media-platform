package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.infrastructure.InMemoryEntitlementCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class EntitlementServiceTest {

    private EntitlementService service;
    private InMemoryEntitlementCache cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryEntitlementCache();
        service = new EntitlementService(cache, null);
    }

    @Test
    void checkFeatureAccessReturnsDeniedForUnknownSubject() {
        FeatureCheckCommand command = new FeatureCheckCommand("unknown-subject", "render.job.create", "{}");

        AccessDecision decision = service.checkFeatureAccess(command);

        assertNotNull(decision);
        assertEquals("unknown-subject", decision.subjectId());
        assertEquals("render.job.create", decision.featureCode());
        assertFalse(decision.granted());
        assertEquals("no-grant", decision.reason());
    }

    @Test
    void checkFeatureAccessReturnsGrantedForEntitledSubject() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        FeatureCheckCommand command = new FeatureCheckCommand("tenant-1", "render.job.create", "{}");
        AccessDecision decision = service.checkFeatureAccess(command);

        assertTrue(decision.granted());
        assertEquals("explicit-grant", decision.reason());
    }

    @Test
    void checkFeatureDelegatesToCheckFeatureAccess() {
        FeatureCheckCommand command = new FeatureCheckCommand("tenant-1", "some.feature", "{}");

        AccessDecision decision1 = service.checkFeature(command);
        AccessDecision decision2 = service.checkFeatureAccess(command);

        assertEquals(decision1.granted(), decision2.granted());
        assertEquals(decision1.reason(), decision2.reason());
    }

    @Test
    void explainAccessReturnsGrantedMessageForEntitledFeature() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        String explanation = service.explainAccess("tenant-1", "render.job.create");

        assertTrue(explanation.contains("GRANTED"));
        assertTrue(explanation.contains("render.job.create"));
        assertTrue(explanation.contains("tenant-1"));
    }

    @Test
    void explainAccessReturnsDeniedMessageForUnknownSubject() {
        String explanation = service.explainAccess("unknown-subject", "some.feature");

        assertTrue(explanation.contains("DENIED"));
        assertTrue(explanation.contains("no entitlements found"));
    }

    @Test
    void explainAccessReturnsDeniedWithQuotaProfile() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        String explanation = service.explainAccess("tenant-1", "other.feature");

        assertTrue(explanation.contains("DENIED"));
        assertTrue(explanation.contains("pro_quota"));
    }

    @Test
    void grantEntitlementCreatesChangedEvent() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );

        EntitlementChangedEvent event = service.grantEntitlement(grant);

        assertNotNull(event);
        assertEquals("tenant-1", event.subjectId());
        assertEquals("entitlement.granted", event.reason());
        assertEquals("billing.contract.activated", event.sourceEventType());
    }

    @Test
    void grantEntitlementStoresSnapshot() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );

        service.grantEntitlement(grant);

        EntitlementSnapshot snapshot = service.getSnapshot("tenant-1");
        assertNotNull(snapshot);
        assertEquals("tenant-1", snapshot.subjectId());
        assertTrue(snapshot.featureCodes().contains("render.job.create"));
        assertEquals("pro_quota", snapshot.quotaProfileCode());
    }

    @Test
    void grantEntitlementUpdatesCache() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );

        service.grantEntitlement(grant);

        EntitlementSnapshot cached = cache.get("tenant-1");
        assertNotNull(cached);
        assertEquals("tenant-1", cached.subjectId());
    }

    @Test
    void getQuotaProfileReturnsDefaultForUnknownSubject() {
        QuotaDecision decision = service.getQuotaProfile("unknown-subject");

        assertNotNull(decision);
        assertEquals("unknown-subject", decision.subjectId());
        assertFalse(decision.allowed());
    }

    @Test
    void getQuotaProfileReturnsProQuotaForProSubject() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        QuotaDecision decision = service.getQuotaProfile("tenant-1");

        assertNotNull(decision);
        assertTrue(decision.allowed());
        assertEquals(10000L, decision.limitValue());
    }

    @Test
    void getQuotaProfileReturnsBasicQuotaForBasicSubject() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "basic_features", "basic_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        QuotaDecision decision = service.getQuotaProfile("tenant-1");

        assertTrue(decision.allowed());
        assertEquals(1000L, decision.limitValue());
    }

    @Test
    void getQuotaProfileReturnsEnterpriseQuota() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "enterprise_features", "enterprise_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        QuotaDecision decision = service.getQuotaProfile("tenant-1");

        assertTrue(decision.allowed());
        assertEquals(100000L, decision.limitValue());
    }

    @Test
    void checkQuotaReturnsAllowedWhenUnderLimit() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        QuotaDecision decision = service.checkQuota("tenant-1", "render.job.create", 5000.0);

        assertTrue(decision.allowed());
    }

    @Test
    void checkQuotaReturnsDeniedWhenOverLimit() {
        EntitlementGrant grant = new EntitlementGrant(
                "tenant-1", "render.job.create", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        service.grantEntitlement(grant);

        QuotaDecision decision = service.checkQuota("tenant-1", "render.job.create", 15000.0);

        assertFalse(decision.allowed());
    }

    @Test
    void getSnapshotReturnsDefaultForUnknownSubject() {
        EntitlementSnapshot snapshot = service.getSnapshot("unknown-subject");

        assertNotNull(snapshot);
        assertEquals("unknown-subject", snapshot.subjectId());
        assertFalse(snapshot.featureCodes().isEmpty());
        assertEquals("pro_quota", snapshot.quotaProfileCode());
    }

    @Test
    void getSnapshotReturnsCachedVersion() {
        EntitlementSnapshot cached = new EntitlementSnapshot(
                "tenant-1", List.of("cached.feature"), "cached_quota", Instant.now().plusSeconds(86400)
        );
        cache.put(cached);

        EntitlementSnapshot snapshot = service.getSnapshot("tenant-1");

        assertEquals("cached_quota", snapshot.quotaProfileCode());
        assertTrue(snapshot.featureCodes().contains("cached.feature"));
    }

    @Test
    void getChangeEventsReturnsAllGrantEvents() {
        EntitlementGrant grant1 = new EntitlementGrant(
                "tenant-1", "feature.a", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        EntitlementGrant grant2 = new EntitlementGrant(
                "tenant-2", "feature.b", "basic_quota", Instant.now().plusSeconds(86400 * 30L)
        );

        service.grantEntitlement(grant1);
        service.grantEntitlement(grant2);

        List<EntitlementChangedEvent> events = service.getChangeEvents();
        assertEquals(2, events.size());
    }

    @Test
    void grantEntitlementMultipleFeaturesAccumulates() {
        EntitlementGrant grant1 = new EntitlementGrant(
                "tenant-1", "feature.a", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );
        EntitlementGrant grant2 = new EntitlementGrant(
                "tenant-1", "feature.b", "pro_quota", Instant.now().plusSeconds(86400 * 30L)
        );

        service.grantEntitlement(grant1);

        FeatureCheckCommand checkA = new FeatureCheckCommand("tenant-1", "feature.a", "{}");
        assertTrue(service.checkFeatureAccess(checkA).granted());

        FeatureCheckCommand checkB = new FeatureCheckCommand("tenant-1", "feature.b", "{}");
        assertFalse(service.checkFeatureAccess(checkB).granted());

        service.grantEntitlement(grant2);
        assertTrue(service.checkFeatureAccess(checkB).granted());
    }
}
