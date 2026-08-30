package com.example.platform.billing.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class I4UsageLineageArchitectureTest {

    private static final Path SHARED_USAGE = Path.of(
            "../shared-kernel/src/main/java/com/example/platform/shared/usage");
    private static final Path EXTENSION_RUNTIME = Path.of(
            "../extension-module/src/main/java/com/example/platform/extension/runtime");
    private static final Path RENDER_MAIN = Path.of(
            "../render-module/src/main/java/com/example/platform/render");
    private static final Path BILLING_MAIN = Path.of("src/main/java/com/example/platform/billing");
    private static final Path SCHEMA = Path.of(
            "../platform-app/src/main/resources/db/migration/V1__initial_schema.sql");

    @Test
    void neutralObservationOwnershipAndRuntimeImportDirection() throws IOException {
        assertTrue(Files.exists(SHARED_USAGE.resolve("ObservedRuntimeUsage.java")));
        for (Path source : javaFiles(EXTENSION_RUNTIME)) {
            assertFalse(code(source).contains("com.example.platform.billing."),
                    "runtime must import no Billing app/domain/pricing/type: " + source);
        }
        assertFalse(code(SHARED_USAGE.resolve("ObservedRuntimeUsage.java"))
                .contains("com.example.platform.billing"));
    }

    @Test
    void observedAndBillableAreDistinctTypesWithoutCommercialFieldsInObservation()
            throws IOException {
        assertNotEquals(ObservedRuntimeUsage.class, BillableUsage.class);
        String observation = code(SHARED_USAGE.resolve("ObservedRuntimeUsage.java"));
        for (String forbidden : List.of(
                "price", "billable", "entitlement", "subscription", "quotaDecision",
                "amountMinor", "currency")) {
            assertFalse(observation.contains(forbidden),
                    "observation contains commercial field/token: " + forbidden);
        }
        String billable = code(BILLING_MAIN.resolve("usage/BillableUsage.java"));
        assertTrue(billable.contains("observedUsageId"));
        assertTrue(billable.contains("meteringRuleVersion"));
        assertTrue(billable.contains("transformationDetails"));
    }

    @Test
    void meteringHasNoDoubleRandomIdentityOrManualObservationShortcut() throws IOException {
        String service = code(BILLING_MAIN.resolve("app/UsageMeteringService.java"));
        assertFalse(Pattern.compile("\\b(?:double|Double|float|Float)\\b")
                .matcher(service).find());
        assertFalse(service.contains("UUID.randomUUID"));
        assertFalse(service.contains("UsageRecord.record("));
        assertFalse(service.contains("ObservedRuntimeUsage.observe("));
        assertFalse(service.contains("default ->"));
    }

    @Test
    void billingRatingCanSeeBillableUsageButNotObservationsOrLegacyUsage() throws IOException {
        String rating = code(BILLING_MAIN.resolve("app/RatingEngine.java"));
        String boundary = code(BILLING_MAIN.resolve("usage/BillingConsumptionBoundary.java"));
        assertTrue(rating.contains("BillableUsage"));
        assertTrue(boundary.contains("BillableUsage"));
        assertFalse(rating.contains("ObservedRuntimeUsage"));
        assertFalse(Pattern.compile("\\bUsageRecord\\b").matcher(rating).find());
        assertFalse(boundary.contains("ObservedRuntimeUsage"));
        assertFalse(Pattern.compile("\\bUsageRecord\\b").matcher(boundary).find());
    }

    @Test
    void schemaHasDistinctIntegralAppendOnlyTablesAndNoRenderShadow() throws IOException {
        String schema = Files.readString(SCHEMA).toLowerCase();
        assertTrue(schema.contains("create table observed_runtime_usage"));
        assertTrue(schema.contains("create table billable_usage"));
        assertFalse(Pattern.compile("create\\s+table\\s+usage_record\\b")
                .matcher(schema).find());
        assertFalse(Pattern.compile("create\\s+table\\s+render_usage_record\\b")
                .matcher(schema).find());
        assertTrue(schema.contains("quantity_base_units bigint"));
        assertTrue(schema.contains("before update or delete on observed_runtime_usage"));
        assertTrue(schema.contains("before update or delete on billable_usage"));
        assertFalse(Files.exists(RENDER_MAIN.resolve(
                "infrastructure/queue/UsageRecordRepository.java")));
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String code(Path source) throws IOException {
        return Files.readString(source).replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("//[^\\n]*", " ");
    }
}
