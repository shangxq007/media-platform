package com.example.platform.payment.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class I6PaymentAuthorityArchitectureTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void schemaDefinesCanonicalAuthorityWithoutRawPayloadColumns() throws Exception {
        String schema = Files.readString(ROOT.resolve(
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"));
        assertTrue(schema.contains("create table payment_transaction"));
        assertTrue(schema.contains("create table payment_command"));
        assertTrue(schema.contains("create table provider_webhook_receipt"));
        assertTrue(schema.contains("create table payment_refund"));
        assertTrue(schema.contains("create table payment_outbox"));
        assertFalse(schema.contains("create table payment_attempt"));
        assertFalse(schema.contains("create table provider_webhook_event"));
        String paymentSchema = schema.substring(schema.indexOf("create table payment_transaction"),
                schema.indexOf("create table subscription_contract"));
        assertFalse(paymentSchema.contains("request_payload"));
        assertFalse(paymentSchema.contains("response_payload"));
        assertFalse(paymentSchema.contains("raw_payload"));
    }

    @Test
    void paymentHasOneWriterNoMapBindingNoHeuristicsAndNoCommercialAuthorityImports() throws Exception {
        Path main = ROOT.resolve("payment-module/src/main/java");
        String all = Files.walk(main).filter(path -> path.toString().endsWith(".java"))
                .map(path -> { try { return Files.readString(path); } catch (Exception e) { throw new RuntimeException(e); } })
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(all.contains("class PaymentTransactionAuthority"));
        assertTrue(all.contains("class PaymentTransactionJdbcRepository"));
        assertFalse(all.contains("CheckoutPaymentBindingRegistry"));
        assertFalse(all.contains("@Autowired(required = false)"));
        assertFalse(all.contains("contains(\"hs\")"));
        assertFalse(all.contains("startsWith(\"hs\")"));
        assertFalse(all.contains("findById(String"));
        assertFalse(all.contains("loadAll("));
        assertFalse(all.contains("com.example.platform.entitlement"));
        assertFalse(all.contains("com.example.platform.billing.domain.Subscription"));
        assertFalse(all.contains("RuntimeEligibility"));
        assertFalse(all.matches("(?s).*\\b(?:double|Double|float|Float)\\s+\\w*(?:amount|price|refund|capture).*"));

        List<Path> writers = Files.walk(main).filter(path -> path.toString().endsWith(".java"))
                .filter(path -> { try {
                    String source = Files.readString(path);
                    return source.contains("INSERT INTO payment_transaction")
                            || source.contains("UPDATE payment_transaction")
                            || source.contains("INSERT INTO provider_webhook_receipt")
                            || source.contains("INSERT INTO payment_refund")
                            || source.contains("INSERT INTO payment_outbox");
                } catch (Exception e) { throw new RuntimeException(e); } })
                .toList();
        assertEquals(java.util.List.of(main.resolve(
                "com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java")), writers);

        String authority = Files.readString(main.resolve(
                "com/example/platform/payment/app/PaymentTransactionAuthority.java"));
        assertFalse(authority.contains("PaymentSettlementProjectionPort"));
    }
}
