package com.example.platform.commerce.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class I7ProductCatalogArchitectureTest {
    private static final Path ROOT = repositoryRoot();

    @Test void canonicalCatalogHasOneSqlWriterAndNoProcessLocalAuthority() throws Exception {
        String main = readTree(ROOT.resolve("commerce-module/src/main/java"));
        assertEquals(1, occurrences(main, "INSERT INTO commerce_product"));
        assertEquals(1, occurrences(main, "INSERT INTO commercial_offering"));
        assertFalse(main.contains("private final List<CanonicalProduct> products"));
        assertFalse(main.contains("enterprise_monthly\".equals"));
    }

    @Test void commerceHasNoFloatingMoneyExecutionCostH1OrPlanCapabilitySemantics() throws Exception {
        String main = readTree(ROOT.resolve("commerce-module/src/main/java"));
        assertFalse(main.matches("(?s).*\\b(?:double|Double|float|Float)\\s+\\w*(?:amount|price|cost|credit|balance).*"));
        assertFalse(main.contains("ExecutionCost"));
        assertFalse(main.contains("com.example.platform.execution.compatibility"));
        assertFalse(main.contains("com.example.platform.workerfabric"));
        assertFalse(main.contains("ProTimeline") || main.contains("EnterpriseRenderGraph") || main.contains("Capability.proOnly"));
    }

    @Test void v1AndSnapshotsExposeVersionedAuthorityFields() throws Exception {
        String schema = Files.readString(ROOT.resolve("platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        for (String token : new String[]{"create table commercial_offering", "create table product_catalog_command",
                "offering_version", "commercial_price_ref", "amount_minor_snapshot", "currency_code_snapshot",
                "tenant_scope", "market_scope", "payload_fingerprint"}) assertTrue(schema.contains(token), token);
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !(Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
                && Files.isDirectory(candidate.resolve("commerce-module")))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) throw new IllegalStateException("repository root not found");
        return candidate;
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder out = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) out.append(Files.readString(p));
        }
        return out.toString();
    }
    private static int occurrences(String value, String token) { return value.split(java.util.regex.Pattern.quote(token), -1).length - 1; }
}
