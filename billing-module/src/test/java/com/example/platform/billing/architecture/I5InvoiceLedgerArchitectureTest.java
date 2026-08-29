package com.example.platform.billing.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class I5InvoiceLedgerArchitectureTest {
    private static final Path ROOT = locateRoot();

    @Test
    void invoiceAndLedgerSchemaAreTenantScopedVersionedExactAndIdempotent() throws IOException {
        String schema = Files.readString(ROOT.resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        String invoice = table(schema, "billing_invoice");
        String lines = table(schema, "invoice_line_item");
        String ledger = table(schema, "billing_ledger_entry");
        assertTrue(invoice.contains("tenant_id varchar(64) not null"));
        assertTrue(invoice.contains("version bigint not null"));
        assertTrue(schema.contains("create table billing_invoice_command"));
        assertTrue(lines.contains("rated_usage_id varchar(64)"));
        assertTrue(lines.contains("quantity_base_units bigint not null"));
        assertFalse(lines.contains("double precision"));
        assertTrue(ledger.contains("idempotency_key varchar(255) not null"));
        assertTrue(ledger.contains("payload_fingerprint varchar(64) not null"));
        assertTrue(ledger.contains("unique (tenant_id, reference_type, reference_id, entry_type)"));
        assertTrue(ledger.contains("check (entry_type in"));
        assertTrue(ledger.contains("entry_type = 'ADJUSTMENT' or amount_minor >= 0"));
    }

    @Test
    void invoiceAndLedgerHaveOneWriterAndNoUnscopedLoadAll() throws IOException {
        assertEquals(1, writerFiles("billing_invoice"));
        assertEquals(1, writerFiles("invoice_line_item"));
        assertEquals(1, writerFiles("billing_ledger_entry"));
        String ledgerRepository = Files.readString(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingLedgerJdbcRepository.java"));
        String invoiceRepository = Files.readString(ROOT.resolve(
                "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingInvoiceRepository.java"));
        assertFalse(ledgerRepository.contains("loadAll"));
        assertFalse(invoiceRepository.contains("findById("));
        assertFalse(invoiceRepository.contains("findByContractId("));
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
