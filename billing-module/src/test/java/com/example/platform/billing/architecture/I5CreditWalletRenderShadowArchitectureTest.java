package com.example.platform.billing.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class I5CreditWalletRenderShadowArchitectureTest {
    private static final Path ROOT = locateRoot();
    private static final Pattern FLOAT_TYPE = Pattern.compile("\\b(?:double|Double|float|Float)\\b");

    @Test
    void walletSchemaIsVersionedTenantScopedDurableAndExact() throws IOException {
        String schema = Files.readString(ROOT.resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        String wallet = table(schema, "credit_wallet");
        String transaction = table(schema, "credit_transaction");
        assertTrue(wallet.contains("version bigint not null"));
        assertTrue(wallet.contains("check (balance_minor >= 0)"));
        assertTrue(schema.contains("create unique index uq_credit_wallet_principal"));
        assertTrue(schema.contains("create table credit_reservation"));
        assertTrue(schema.contains("create table credit_wallet_command"));
        assertTrue(transaction.contains("tenant_id varchar(64) not null"));
        assertTrue(transaction.contains("idempotency_key varchar(255) not null"));
        assertEquals(1, writerFiles("credit_wallet"));
        assertEquals(1, writerFiles("credit_reservation"));
        assertEquals(1, writerFiles("credit_transaction"));
        assertEquals(1, writerFiles("credit_wallet_command"));
    }

    @Test
    void walletAuthorityHasNoProcessMapUnscopedLoadOrFloatingMoney() throws IOException {
        Path service = ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/app/CreditWalletService.java");
        Path repository = ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/infrastructure/CreditWalletJdbcRepository.java");
        String source = Files.readString(service) + Files.readString(repository);
        assertFalse(source.contains("ConcurrentHashMap"));
        assertFalse(source.contains("loadAll"));
        assertFalse(FLOAT_TYPE.matcher(source).find());
    }

    @Test
    void renderOwnsNoCreditOrCommercialPriceShadow() throws IOException {
        Path billing = ROOT.resolve("render-module/src/main/java/com/example/platform/render/infrastructure/billing");
        assertFalse(Files.exists(billing.resolve("policy/CreditSystem.java")));
        assertFalse(Files.exists(billing.resolve("policy/PricingEngine.java")));
        if (Files.exists(billing)) {
            try (var paths = Files.walk(billing)) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("CreditSystem"), () -> "Render credit shadow caller: " + path);
                    assertFalse(source.contains("PricingEngine"), () -> "Render price shadow caller: " + path);
                }
            }
        }
    }

    private static long writerFiles(String table) throws IOException {
        try (var paths = Files.walk(ROOT.resolve("billing-module/src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path).toLowerCase();
                            return source.contains("insert into " + table)
                                    || source.contains("update " + table)
                                    || source.contains("delete from " + table);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).count();
        }
    }

    private static String table(String schema, String name) {
        int start = schema.indexOf("create table " + name);
        int end = schema.indexOf("create table ", start + 1);
        return schema.substring(start, end < 0 ? schema.length() : end);
    }

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}
