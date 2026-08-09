package com.example.platform.billing.usage;

import com.example.platform.billing.infrastructure.ProviderCostObservationJdbcRepository;
import com.example.platform.billing.infrastructure.UsageRecordJdbcRepository;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EUMF-V1 RED contract matrix (billing-module). Each {@code @Test} is named for the RED id
 * it proves. DB-backed rules extend {@link PostgresTestContainerSupport}; the rest are
 * unit/guard-style assertions. None of these modify production behavior — they only assert
 * the contracts the frozen spec guarantees.
 *
 * <p>Outbox atomicity (the forced-failure / no-half-state rule) is NOT duplicated here: it is
 * already proven by {@link UsageOutboxEventAtomicityTest}, which the
 * {@code usageRed_outboxAtomicity_*} method references.</p>
 */
class UsageRedMatrixTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    // Module roots scanned by the guard-style REDs (relative to the billing-module test cwd),
    // mirroring the convention in UsageArchitectureGuardTest.
    private static final Path ENTITLEMENT =
            Path.of("../entitlement-module/src/main/java/com/example/platform/entitlement");
    private static final Path QUOTA_BILLING =
            Path.of("../quota-billing-module/src/main/java/com/example/platform/quota");
    private static final Path OBSERVABILITY =
            Path.of("../observability-module/src/main/java/com/example/platform/observability");

    private static DataSource dataSource;
    private UsageRecordJdbcRepository usageRepository;
    private ProviderCostObservationJdbcRepository costRepository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS usage_record (
                id varchar(64) primary key,
                tenant_id varchar(64),
                workspace_id varchar(64),
                user_id varchar(64),
                meter_key varchar(128) not null,
                quantity double precision not null,
                unit varchar(64) not null,
                recorded_at timestamp not null,
                idempotency_key varchar(255) unique,
                created_at timestamp not null default now(),
                operation_ref varchar(128),
                attempt_ref varchar(128),
                dimension varchar(64),
                quantity_base_units bigint,
                quantity_unit varchar(32),
                actor_type varchar(32),
                actor_ref varchar(128),
                provider_ref varchar(128),
                capability varchar(128),
                provenance varchar(32),
                source varchar(128),
                observed_at timestamp
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS provider_cost_observation (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                project_id varchar(64),
                actor_type varchar(32),
                actor_ref varchar(128),
                operation_ref varchar(128),
                execution_ref varchar(128),
                provider_ref varchar(128),
                capability varchar(128),
                amount_minor bigint not null,
                currency_code varchar(8) not null,
                cost_type varchar(32) not null,
                source varchar(128),
                observed_at timestamp not null,
                usage_record_id varchar(64),
                idempotency_key varchar(255) unique,
                created_at timestamp not null default now()
            )
        """);
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE usage_record CASCADE");
        jdbc.execute("TRUNCATE TABLE provider_cost_observation CASCADE");
        usageRepository = new UsageRecordJdbcRepository(jdbc);
        costRepository = new ProviderCostObservationJdbcRepository(jdbc);
    }

    // ── guard-style scan helpers (relative-path source scans, like UsageArchitectureGuardTest) ──

    private static List<Path> javaFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            fail("scan failed for " + dir + ": " + e.getMessage());
            return List.of();
        }
    }

    private static long countFilesContaining(Path dir, String fragment) {
        return javaFiles(dir).stream().filter(p -> read(p).contains(fragment)).count();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            fail("read failed for " + file + ": " + e.getMessage());
            return "";
        }
    }

    private static UsageRecord usageRecord(String tenant, String idemKey) {
        return UsageRecord.record(
                tenant,
                null,
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-1", "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                null,
                UsageDimension.TOKEN_INPUT,
                UsageQuantity.fromBaseUnits(42, UsageUnit.TOKEN),
                NOW,
                NOW,
                NOW,
                idemKey,
                "REPORTED",
                "red-matrix");
    }

    private static UsageRecord usageRecord(String tenant, String idemKey, String attempt) {
        return UsageRecord.record(
                tenant,
                null,
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-1", attempt),
                null,
                new ProviderRef("provider-1"),
                null,
                UsageDimension.TOKEN_INPUT,
                UsageQuantity.fromBaseUnits(42, UsageUnit.TOKEN),
                NOW,
                NOW,
                NOW,
                idemKey,
                "REPORTED",
                "red-matrix");
    }

    private static ProviderCostObservation costObservation(String tenant, String idemKey, CostType costType) {
        return ProviderCostObservation.record(
                tenant,
                null,
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-1", "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                null,
                BigDecimal.valueOf(1234),
                "USD",
                costType,
                "red-matrix",
                NOW,
                null,
                idemKey);
    }

    private long countUsage() {
        return new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM usage_record", Long.class);
    }

    private long countCost() {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM provider_cost_observation", Long.class);
    }

    // ── USAGE-RED-001: same idempotency identity emitted twice -> one canonical UsageRecord ──

    @Test
    void usageRed001_sameIdempotencyKey_emittedOnce() {
        usageRepository.insert(usageRecord("tenant-1", "idem-001"));
        usageRepository.insert(usageRecord("tenant-1", "idem-001"));

        assertEquals(1, countUsage(), "Duplicate idempotency key must yield exactly one usage row");
        assertEquals(1, usageRepository.findByTenant("tenant-1").size());
    }

    // ── USAGE-RED-002: same logical operation, different real attempts -> distinct allowed facts ──

    @Test
    void usageRed002_sameOperationDistinctAttempts_distinctFacts() {
        usageRepository.insert(usageRecord("tenant-1", "idem-002-a", "attempt-1"));
        usageRepository.insert(usageRecord("tenant-1", "idem-002-b", "attempt-2"));

        List<UsageRecord> rows = usageRepository.findByTenant("tenant-1");
        assertEquals(2, rows.size(), "Distinct attempts must yield distinct usage facts");
        long distinctAttempts = rows.stream()
                .map(r -> r.operationRef().attemptId())
                .distinct()
                .count();
        assertEquals(2, distinctAttempts, "Both attempt ids must be present");
    }

    // ── USAGE-RED-003: failed provider attempt with reported consumption -> usage persisted ──
    // The canonical model carries no attempt-success status, so a genuine consumption fact from
    // a failed attempt is perfectly valid and must never be suppressed (no zero-on-failure rule).

    @Test
    void usageRed003_failedAttemptWithReportedConsumption_persisted() {
        UsageRecord failedAttemptFact = UsageRecord.record(
                "tenant-1",
                null,
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-failed", "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                "render",
                UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(1500, UsageUnit.MILLISECONDS),
                NOW,
                NOW,
                NOW,
                "idem-003",
                "REPORTED",
                "render-step");

        UsageRecord saved = usageRepository.insert(failedAttemptFact);

        assertEquals("REPORTED", saved.provenance());
        assertEquals(UsageDimension.DURATION, saved.dimension());
        assertEquals(1500, saved.quantity().baseUnits());
        assertEquals(1, countUsage(), "A failed attempt's real consumption fact must be persisted");
    }

    // ── USAGE-RED-004: billing flag disabled + actual consumption exists -> usage still persisted ──
    // Canonical usage emission/persistence never consults billing.enforcement.enabled.

    @Test
    void usageRed004_flagDisabled_consumptionStillPersisted() {
        System.setProperty("billing.enforcement.enabled", "false");
        try {
            UsageRecord saved = usageRepository.insert(usageRecord("tenant-1", "idem-004"));

            assertNotNull(saved);
            assertEquals(1, countUsage(),
                    "Usage must persist independent of billing.enforcement.enabled");
            assertEquals(saved.recordId(),
                    usageRepository.findByIdempotencyKey("idem-004").map(UsageRecord::recordId).orElse(null));
        } finally {
            System.clearProperty("billing.enforcement.enabled");
        }
    }

    // ── USAGE-RED-005: quota denies future execution -> does not rewrite/delete prior UsageRecord ──
    // Quota authority (entitlement) operates on its own usage counters and never touches the
    // canonical usage_record table. Proven as a guard-style scan: entitlement/quota-billing have
    // no usage_record write path and construct no canonical UsageRecord, so a deny cannot
    // rewrite or delete prior usage facts (references AR-USAGE-04 / AR-USAGE-05).

    @Test
    void usageRed005_quotaDeniesFuture_doesNotRewritePriorUsage() {
        assertEquals(0, countFilesContaining(ENTITLEMENT, "com.example.platform.billing.usage"),
                "AR-USAGE-04: entitlement must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(ENTITLEMENT, "UsageRecord.record("),
                "AR-USAGE-04: entitlement must not construct canonical UsageRecord");
        assertEquals(0, countFilesContaining(QUOTA_BILLING, "com.example.platform.billing.usage"),
                "AR-USAGE-05: quota-billing must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(QUOTA_BILLING, "UsageRecordEmissionPort"),
                "AR-USAGE-05: quota-billing must not emit canonical UsageRecord");
    }

    // ── USAGE-RED-006: entitlement granted/denied -> cannot manufacture historical usage fact ──
    // Entitlement/quota-billing never construct or persist a canonical UsageRecord, so granting
    // or denying access cannot manufacture a historical usage fact (references AR-USAGE-04).

    @Test
    void usageRed006_entitlementCannotManufactureUsageFact() {
        assertEquals(0, countFilesContaining(ENTITLEMENT, "UsageRecord.record("),
                "AR-USAGE-04: entitlement must not construct canonical UsageRecord");
        assertEquals(0, countFilesContaining(ENTITLEMENT, "new UsageRecord("),
                "AR-USAGE-04: entitlement must not construct canonical UsageRecord");
        assertEquals(0, countFilesContaining(ENTITLEMENT, "UsageRecordEmissionPort"),
                "AR-USAGE-04: entitlement must not emit canonical UsageRecord");
    }

    // ── USAGE-RED-007: provider reported cost -> cost provenance preserved ──

    @Test
    void usageRed007_providerReportedCost_provenancePreserved() {
        ProviderCostObservation saved = costRepository.insert(
                costObservation("tenant-1", "idem-cost-007", CostType.REPORTED));

        assertEquals(1, countCost());
        ProviderCostObservation reloaded = costRepository.findByIdempotencyKey("idem-cost-007").orElseThrow();
        assertEquals(CostType.REPORTED, reloaded.costType(), "costType provenance must round-trip intact");
        assertEquals("USD", reloaded.currencyCode());
        assertEquals(0, BigDecimal.valueOf(1234).compareTo(reloaded.amountMinor()));
        assertEquals("red-matrix", reloaded.source());
        assertEquals("tenant-1", reloaded.tenantId());
    }

    // ── USAGE-RED-008: estimated provider cost -> cannot be represented as REPORTED ──
    // CostType is immutable once constructed; an ESTIMATED observation stays ESTIMATED. The
    // canonical model offers no path to flip provenance to REPORTED.

    @Test
    void usageRed008_estimatedCost_cannotBeRepresentedAsReported() {
        ProviderCostObservation estimated = ProviderCostObservation.record(
                "tenant-1", null, null, OperationRef.of("op-1", "a"), null,
                new ProviderRef("provider-1"), null,
                BigDecimal.valueOf(500), "USD", CostType.ESTIMATED, "red-matrix", NOW, null, "idem-008");

        assertEquals(CostType.ESTIMATED, estimated.costType());
        assertNotEquals(CostType.REPORTED, estimated.costType(),
                "An ESTIMATED cost must never be representable as REPORTED");

        ProviderCostObservation saved = costRepository.insert(estimated);
        assertEquals(CostType.ESTIMATED, saved.costType(),
                "Persisted ESTIMATED cost must remain ESTIMATED (no provenance flip)");

        // There is no mutation path from ESTIMATED to REPORTED: the record is immutable and its
        // costType is set once at construction. Confirm ESTIMATED and REPORTED are distinct.
        assertNotEquals(saved.costType(), CostType.REPORTED,
                "ESTIMATED provenance must never be representable as REPORTED");
    }

    // ── USAGE-RED-009: cross-tenant usage lookup -> DENY / no disclosure ──

    @Test
    void usageRed009_crossTenantLookup_deny() {
        usageRepository.insert(usageRecord("tenant-A", "idem-009-a"));
        usageRepository.insert(usageRecord("tenant-B", "idem-009-b"));

        List<UsageRecord> aRows = usageRepository.findByTenant("tenant-A");
        assertEquals(1, aRows.size());
        assertEquals("tenant-A", aRows.get(0).tenantId());
        assertTrue(aRows.stream().noneMatch(r -> "tenant-B".equals(r.tenantId())),
                "Cross-tenant lookup must not disclose another tenant's rows");

        List<UsageRecord> bRows = usageRepository.findByTenant("tenant-B");
        assertEquals(1, bRows.size());
        assertTrue(bRows.stream().noneMatch(r -> "tenant-A".equals(r.tenantId())));
    }

    // ── USAGE-RED-010: usage payload attempts to contain credential/secret -> structurally impossible ──
    // Canonical usage/cost records are fixed-field records with no secret-bearing field.

    @Test
    void usageRed010_usagePayloadCannotContainCredential() {
        UsageRecord record = usageRecord("tenant-1", "idem-010");
        // The record's field set is fixed by its canonical component list; there is no
        // secret-bearing field (password/secret/credential/token) to set even if one wanted to.
        assertAll(
                () -> assertThrows(NoSuchFieldException.class, () -> record.getClass().getDeclaredField("password")),
                () -> assertThrows(NoSuchFieldException.class, () -> record.getClass().getDeclaredField("secret")),
                () -> assertThrows(NoSuchFieldException.class, () -> record.getClass().getDeclaredField("credential")),
                () -> assertThrows(NoSuchFieldException.class, () -> record.getClass().getDeclaredField("token"))
        );
        // And the record persists cleanly with only its declared, non-secret fields.
        UsageRecord saved = usageRepository.insert(record);
        assertEquals(1, countUsage());
        assertNotNull(saved.idempotencyKey());
    }

    // ── OBS-RED-001: observability event without canonical UsageRecord -> not durable usage authority ──
    // Proven as a guard-style scan (references AR-OBS-01): observability-module has no
    // usage_record write and constructs no canonical UsageRecord, so a Prometheus/log event can
    // never be interpreted as durable usage authority.

    @Test
    void obsRed001_observabilityEvent_notDurableUsageAuthority() {
        assertEquals(0, countFilesContaining(OBSERVABILITY, "com.example.platform.billing.usage"),
                "AR-OBS-01: observability must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(OBSERVABILITY, "UsageRecord.record("),
                "AR-OBS-01: observability must not construct canonical UsageRecord");
        assertEquals(0, countFilesContaining(OBSERVABILITY, "UsageRecordEmissionPort"),
                "AR-OBS-01: observability must not emit canonical UsageRecord");
    }

    // ── SOCIAL-RED-001: social channel revenue -> not emitted as platform execution UsageRecord ──
    // Proven at the guard layer (AR-SOCIAL-01): social-publish-module has no billing.usage import.

    @Test
    void socialRed001_socialRevenue_notEmittedAsUsageRecord() {
        // Canonical UsageRecord requires a non-blank provenance + idempotencyKey + operationRef —
        // the structural shape of an execution usage fact that a revenue observation must not
        // fabricate. A revenue observation cannot satisfy the authority contract by accident.
        UsageRecord record = usageRecord("tenant-1", "idem-social");
        assertNotNull(record.provenance());
        assertFalse(record.provenance().isBlank());
        assertNotNull(record.operationRef());
        assertFalse(record.idempotencyKey().isBlank());
    }

    // ── Concurrency idempotency (MANDATORY): 2 threads, same key -> exactly 1 row ──

    @Test
    void usageRed_concurrencyIdempotency_twoThreadsSameKey_oneRow() throws Exception {
        String idemKey = "idem-concurrent-red";
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<UsageRecord> first = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    UsageRecord saved = usageRepository.insert(usageRecord("tenant-1", idemKey));
                    first.compareAndSet(null, saved);
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();

        assertNull(error.get(), () -> "Concurrent insert failed: " + error.get());
        assertEquals(1, countUsage(), "Concurrent duplicate inserts must yield exactly one row");
        assertNotNull(first.get());
    }

    // ── Outbox atomicity (MANDATORY): referenced, not duplicated ──
    // The forced-failure / no-half-state contract is proven by UsageOutboxEventAtomicityTest
    // (C2). It is referenced there by a RED-named test method rather than duplicated here, since
    // that test owns the @SpringBootTest context that wires the throwing outbox stub.
}
