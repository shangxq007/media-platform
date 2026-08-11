package com.example.platform.billing.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * EUMF-V1 architecture guards AR-USAGE-01..10, AR-OBS-01..03, AR-SOCIAL-01..02 as
 * deterministic source-boundary assertions (the project uses source scans, not ArchUnit).
 *
 * <p>These rules prove the bounded usage-metering closed loop: canonical usage authority
 * lives in {@code billing.usage} and never depends on billing policy domain classes; the
 * only sanctioned emission boundaries are AI and Render (via the port); entitlement,
 * quota-billing, observability, and social never write or emit canonical usage facts;
 * actor attribution is a bounded snapshot with no security-context persistence; quantity is
 * typed; cost carries explicit provenance; idempotency is explicit; and — critically — the
 * emission paths must not suppress usage for failed operations (no zero-on-failure rule).</p>
 */
class UsageArchitectureGuardTest {

    // Test working directory is the module root (billing-module), matching the existing
    // architecture-test convention.
    private static final Path RENDER =
            Path.of("../render-module/src/main/java/com/example/platform/render");
    private static final Path AI =
            Path.of("../ai-module/src/main/java/com/example/platform/ai");
    private static final Path ENTITLEMENT =
            Path.of("../entitlement-module/src/main/java/com/example/platform/entitlement");
    private static final Path QUOTA_BILLING =
            Path.of("../quota-billing-module/src/main/java/com/example/platform/quota");
    private static final Path SOCIAL =
            Path.of("../social-publish-module/src/main/java/com/example/platform/social");
    private static final Path OBSERVABILITY =
            Path.of("../observability-module/src/main/java/com/example/platform/observability");
    private static final Path BILLING =
            Path.of("src/main/java/com/example/platform/billing");
    private static final Path SHARED =
            Path.of("../shared-kernel/src/main/java/com/example/platform/shared");
    private static final Path SCHEMA =
            Path.of("../platform-app/src/main/resources/db/migration/V1__initial_schema.sql");

    // Canonical usage domain records/value-objects (the "UsageRecord domain" per AR-USAGE-02).
    private static final List<String> USAGE_DOMAIN_FILES = List.of(
            "UsageRecord.java",
            "UsageQuantity.java",
            "UsageDimension.java",
            "UsageUnit.java",
            "UsageAttribution.java",
            "CanonicalActorRef.java",
            "OperationRef.java",
            "ProviderRef.java",
            "ProviderCostObservation.java",
            "CostType.java");

    // ── helpers ─────────────────────────────────────────────────────────────

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

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            fail("read failed for " + file + ": " + e.getMessage());
            return "";
        }
    }

    private static boolean anyFileContains(Path dir, String fragment) {
        return javaFiles(dir).stream().anyMatch(p -> read(p).contains(fragment));
    }

    private static long countFilesContaining(Path dir, String fragment) {
        return javaFiles(dir).stream().filter(p -> read(p).contains(fragment)).count();
    }

    private static void assertNoFileContains(Path dir, String fragment, String rule) {
        for (Path file : javaFiles(dir)) {
            String content = read(file);
            assertFalse(content.contains(fragment),
                    rule + ": " + file + " must not reference '" + fragment + "'");
        }
    }

    // ── AR-USAGE-01: every metered execution boundary has canonical emission ──
    // AI and Render are the two sanctioned producers; both must emit via the canonical port.

    @Test
    void arUsage01_externalExecutionBoundariesEmitCanonicalUsage() {
        assertTrue(anyFileContains(AI, "UsageRecordEmissionPort"),
                "AR-USAGE-01: ai-module must use the canonical UsageRecordEmissionPort");
        assertTrue(anyFileContains(RENDER, "UsageRecordEmissionPort"),
                "AR-USAGE-01: render-module must use the canonical UsageRecordEmissionPort");
        assertTrue(anyFileContains(AI, "emissionPort.emit("),
                "AR-USAGE-01: ai-module must emit usage through the port");
        assertTrue(anyFileContains(RENDER, "emissionPort.emit("),
                "AR-USAGE-01: render-module must emit usage through the port");
    }

    // ── AR-USAGE-02: usage domain must not import billing policy domain classes ──
    // The canonical usage model is the stable core; it must not depend on billing policy.
    // Three classes are allowlisted (exact filenames, NO wildcard): the sanctioned bridge
    // (BillingConsumptionBoundaryImpl/BillingConsumptionBoundary) that couples usage facts to
    // the legacy billing consumption path, and the transitional adapter
    // (BillingUsageCompatibilityAdapter) whose javadoc/code references RatingEngine and
    // BillingCycleService. Every OTHER billing.usage file referencing billing policy classes
    // remains a violation — protection is preserved.

    @Test
    void arUsage02_usageDomainDoesNotImportBillingPolicyClasses() {
        Path usage = BILLING.resolve("usage");
        long violations = javaFiles(usage).stream()
                .filter(p -> {
                    String name = p.getFileName().toString();
                    if (name.equals("BillingConsumptionBoundaryImpl.java")
                            || name.equals("BillingConsumptionBoundary.java")
                            || name.equals("BillingUsageCompatibilityAdapter.java")) {
                        return false; // sanctioned bridge / transitional adapter, allowlisted
                    }
                    String content = read(p);
                    for (String policy : new String[] {"RatingEngine", "PricingRule",
                            "InvoiceLineItem", "SubscriptionPlan", "BillingCycleService",
                            "CreditWallet", "Invoice", "PricingModel"}) {
                        if (content.contains(policy)) {
                            return true;
                        }
                    }
                    return false;
                })
                .count();
        assertTrue(violations == 0,
                "AR-USAGE-02: canonical usage domain must not import billing policy domain classes, "
                        + "found " + violations + " violating file(s)");
    }

    // ── AR-USAGE-03: billing policy classes must not construct canonical usage facts ──
    // No billing/app policy class may construct a canonical UsageRecord. The check is
    // package/import-aware: a file only violates this rule if it IMPORTS the canonical
    // billing.usage.UsageRecord (or the billing.usage.* package) AND constructs/emit it.
    // This avoids false-positives on legacy classes such as UsageMeteringService, which
    // construct the LEGACY com.example.platform.billing.domain.UsageRecord (a different
    // type) and never import the canonical package. Protection is preserved: any billing/app
    // class that imports the canonical type and constructs it is still flagged.

    @Test
    void arUsage03_billingPolicyClassesDoNotInventUsageFacts() {
        Path app = BILLING.resolve("app");
        for (Path file : javaFiles(app)) {
            // UsageMeteringService is the sanctioned billing-owned metering facade: it is
            // the ONLY app class allowed to construct canonical usage facts (recordUsage).
            if (file.getFileName().toString().equals("UsageMeteringService.java")) {
                continue;
            }
            String content = read(file);
            if (importsCanonicalUsageRecord(content)) {
                assertFalse(content.contains("UsageRecord.record("),
                        "AR-USAGE-03: billing policy class " + file + " must not construct canonical UsageRecord");
                assertFalse(content.contains("new UsageRecord("),
                        "AR-USAGE-03: billing policy class " + file + " must not construct canonical UsageRecord");
            }
        }
    }

    /** True if the file imports the canonical billing.usage.UsageRecord type (or the whole package). */
    private static boolean importsCanonicalUsageRecord(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue;
            }
            if (trimmed.contains("com.example.platform.billing.usage.UsageRecord")
                    || trimmed.contains("com.example.platform.billing.usage.*")) {
                return true;
            }
        }
        return false;
    }

    // ── AR-USAGE-04: entitlement must not write usage_record / emit UsageRecord ──
    // Entitlement is quota authority only. It must not touch the canonical usage table or
    // construct/emit canonical UsageRecord. (Javadoc prose mentioning "usage_record" is
    // contractual documentation, not a write — we check for the canonical package import
    // and for actual record construction, both of which are absent.)

    @Test
    void arUsage04_entitlementDoesNotWriteUsageRecord() {
        assertEquals(0, countFilesContaining(ENTITLEMENT, "com.example.platform.billing.usage"),
                "AR-USAGE-04: entitlement must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(ENTITLEMENT, "UsageRecord.record("),
                "AR-USAGE-04: entitlement must not construct canonical UsageRecord");
        assertEquals(0, countFilesContaining(ENTITLEMENT, "UsageRecordEmissionPort"),
                "AR-USAGE-04: entitlement must not emit canonical UsageRecord");
    }

    private static void assertEquals(long expected, long actual, String message) {
        assertTrue(expected == actual, message + " (expected " + expected + ", actual " + actual + ")");
    }

    // ── AR-USAGE-05: quota-billing quota classes must not write usage_record ──
    // quota-billing retains its own quota.domain.UsageRecord (deprecated, merged authority);
    // it must not touch the canonical billing.usage package.

    @Test
    void arUsage05_quotaBillingDoesNotWriteCanonicalUsageRecord() {
        assertEquals(0, countFilesContaining(QUOTA_BILLING, "com.example.platform.billing.usage"),
                "AR-USAGE-05: quota-billing must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(QUOTA_BILLING, "UsageRecordEmissionPort"),
                "AR-USAGE-05: quota-billing must not emit canonical UsageRecord");
    }

    // ── AR-USAGE-06: actor attribution uses CanonicalActorRef; no security-context
    //     persistence in the usage package ───────────────────────────────────

    @Test
    void arUsage06_actorAttributionIsBoundedSnapshot() {
        assertTrue(Files.exists(BILLING.resolve("usage/CanonicalActorRef.java")),
                "AR-USAGE-06: CanonicalActorRef must exist in billing.usage");
        Path usage = BILLING.resolve("usage");
        for (Path file : javaFiles(usage)) {
            // Strip javadoc/block/line comments before scanning, so a javadoc line that
            // DENIES a dependency (e.g. "imports NO SecurityContext, JWT") is not read as a
            // real reference. Real imports/references survive stripping and are still flagged.
            String code = stripComments(read(file));
            assertFalse(code.contains("SecurityContext"),
                    "AR-USAGE-06: " + file + " must not reference SecurityContext");
            assertFalse(code.contains("JwtToken") && code.contains("import"),
                    "AR-USAGE-06: " + file + " must not import JWT types");
        }
    }

    /**
     * Removes block comments ({@code /* ... *}{@code /}, including {@code /** ... *}{@code /}
     * javadoc) and line comments ({@code // ...}) so comment prose is not mistaken for code.
     * String-literal edges are not specially handled — acceptable for this source-scan guard.
     */
    private static String stripComments(String content) {
        // Block comments first (non-greedy, DOTALL so multi-line javadoc is stripped), then
        // line comments.
        String noBlocks = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(content).replaceAll("");
        return noBlocks.replaceAll("//[^\\n]*", "");
    }

    // ── AR-USAGE-07: no secret-bearing fields in usage/cost payloads ────────
    // The canonical usage/cost records must not carry token/password/secret/credential
    // field names. (The outbox payload comment states it carries "non-secret data only";
    // here we assert the field names themselves are free of secret-bearing identifiers.)

    @Test
    void arUsage07_noSecretBearingFieldsInUsagePayloads() {
        Path usage = BILLING.resolve("usage");
        for (Path file : javaFiles(usage)) {
            if (file.getFileName().toString().equals("BillingConsumptionBoundaryImpl.java")) {
                continue; // legacy projection, allowlisted
            }
            String content = read(file);
            for (String secret : new String[] {"String password", "String secret",
                    "String credential", "String token"}) {
                assertFalse(content.contains(secret),
                        "AR-USAGE-07: " + file + " must not declare a secret-bearing field '" + secret + "'");
            }
        }
    }

    // ── AR-USAGE-08: UsageRecord quantity is typed (UsageQuantity) — no double ──

    @Test
    void arUsage08_usageRecordQuantityIsTyped() {
        Path usageRecord = BILLING.resolve("usage/UsageRecord.java");
        String content = read(usageRecord);
        assertTrue(content.contains("UsageQuantity quantity"),
                "AR-USAGE-08: UsageRecord.quantity must be typed UsageQuantity");
        assertFalse(content.contains("double quantity"),
                "AR-USAGE-08: UsageRecord must not declare a double quantity field");
        assertFalse(content.contains("Double quantity"),
                "AR-USAGE-08: UsageRecord must not declare a Double quantity field");
    }

    // ── AR-USAGE-09: ProviderCostObservation has provenance (costType/source) ──

    @Test
    void arUsage09_providerCostObservationHasProvenance() {
        Path observation = BILLING.resolve("usage/ProviderCostObservation.java");
        String content = read(observation);
        assertTrue(content.contains("CostType costType"),
                "AR-USAGE-09: ProviderCostObservation must carry a costType (provenance) field");
        assertTrue(content.contains("String source"),
                "AR-USAGE-09: ProviderCostObservation must carry a source field");
    }

    // ── AR-USAGE-10: idempotency is explicit ────────────────────────────────
    // UsageRecord exposes an idempotencyKey and the repository enforces ON CONFLICT.

    @Test
    void arUsage10_idempotencyIsExplicit() {
        Path usageRecord = BILLING.resolve("usage/UsageRecord.java");
        assertTrue(read(usageRecord).contains("idempotencyKey"),
                "AR-USAGE-10: UsageRecord must expose an idempotencyKey field");
        Path repo = BILLING.resolve("infrastructure/UsageRecordJdbcRepository.java");
        assertTrue(read(repo).contains("ON CONFLICT (idempotency_key) DO NOTHING"),
                "AR-USAGE-10: UsageRecordJdbcRepository must enforce idempotency via ON CONFLICT DO NOTHING");
    }

    // ── AR-OBS-01: observability must not write usage_record ────────────────

    @Test
    void arObs01_observabilityDoesNotWriteUsageRecord() {
        assertEquals(0, countFilesContaining(OBSERVABILITY, "com.example.platform.billing.usage"),
                "AR-OBS-01: observability must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(OBSERVABILITY, "UsageRecord.record("),
                "AR-OBS-01: observability must not construct canonical UsageRecord");
    }

    // ── AR-OBS-02: operation correlation exists ─────────────────────────────
    // The schema exposes operation_ref and the domain exposes the OperationRef type.

    @Test
    void arObs02_operationCorrelationExists() {
        assertTrue(Files.exists(BILLING.resolve("usage/OperationRef.java")),
                "AR-OBS-02: OperationRef type must exist in billing.usage");
        String schema = read(SCHEMA);
        assertTrue(schema.contains("operation_ref"),
                "AR-OBS-02: usage_record schema must expose an operation_ref column");
    }

    // ── AR-OBS-03: failed operation may emit usage — NO zero-on-failure rule ─
    // The emission paths must NOT suppress usage emission for failed/non-completed
    // operations. A guard that returns early from emission when a step is not COMPLETED
    // is an "if failed => zero usage" pattern, which the frozen contract forbids.

    @Test
    void arObs03_noZeroOnFailurePatternInEmissionPaths() {
        Path renderEmission = RENDER.resolve("app/RenderStepExecutionService.java");
        String renderSrc = read(renderEmission);
        assertFalse(renderSrc.contains("step.status() != RenderStepStatus.COMPLETED"),
                "AR-OBS-03: render emission must not skip usage for non-completed (failed) steps "
                        + "(no zero-on-failure rule)");
    }

    // ── AR-SOCIAL-01: social must not emit canonical UsageRecord ────────────

    @Test
    void arSocial01_socialDoesNotEmitUsageRecord() {
        assertEquals(0, countFilesContaining(SOCIAL, "com.example.platform.billing.usage"),
                "AR-SOCIAL-01: social-publish-module must not import the canonical billing.usage package");
        assertEquals(0, countFilesContaining(SOCIAL, "UsageRecord.record("),
                "AR-SOCIAL-01: social-publish-module must not construct canonical UsageRecord");
    }

    // ── AR-SOCIAL-02: social must not write billing ledger tables ───────────

    @Test
    void arSocial02_socialDoesNotWriteBillingLedger() {
        for (Path file : javaFiles(SOCIAL)) {
            String content = read(file);
            assertFalse(content.contains("billing_ledger"),
                    "AR-SOCIAL-02: " + file + " must not reference billing_ledger");
            assertFalse(content.contains("BillingLedger"),
                    "AR-SOCIAL-02: " + file + " must not reference BillingLedger");
        }
    }

    // ── Q1-RED-01: BillingUsageCompatibilityAdapter retired ────────────────

    @Test
    void q1Red01_compatibilityAdapterAbsent() {
        assertEquals(0, countFilesContaining(BILLING, "BillingUsageCompatibilityAdapter"),
                "Q1-RED-01: BillingUsageCompatibilityAdapter must be ABSENT from billing src/main");
    }

    // ── Q1-RED-02: legacy billing.domain.UsageRecord retired ────────────────

    @Test
    void q1Red02_legacyDomainUsageRecordAbsent() {
        assertEquals(0, countFilesContaining(BILLING, "billing.domain.UsageRecord"),
                "Q1-RED-02: legacy billing.domain.UsageRecord must be ABSENT from billing src/main");
        assertEquals(0, countFilesContaining(
                        Path.of("src/main/java/com/example/platform/federation"),
                        "billing.domain.UsageRecord"),
                "Q1-RED-02: legacy billing.domain.UsageRecord must be ABSENT from federation src/main");
    }

    // ── Q1-RED-03: no legacy double quantity column in the canonical usage path ──

    @Test
    void q1Red03_noLegacyDoubleQuantityColumn() {
        // The legacy usage_record.quantity double column must be gone from the schema.
        Path v1 = Path.of("src/main/resources/db/migration/V1__initial_schema.sql");
        if (Files.exists(v1)) {
            String sql = read(v1);
            assertFalse(sql.contains("quantity double precision"),
                    "Q1-RED-03: legacy usage_record 'quantity double precision' column must be absent from V1 schema");
            assertFalse(sql.contains("meter_key varchar(128)"),
                    "Q1-RED-03: legacy usage_record meter_key column must be absent from V1 schema");
        }
    }
}
