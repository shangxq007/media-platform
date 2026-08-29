package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Phase I9 proof that accepted commercial-authority shadows are physically retired. */
class I9ShadowRetirementArchitectureTest {

    private static final List<String> ACCEPTED_DELETE_SHADOW_PATHS = List.of(
            "render-module/src/main/java/com/example/platform/render/app/QuotaUsageRepository.java",
            "render-module/src/main/java/com/example/platform/render/app/RenderQuotaService.java",
            "quota-billing-module/src/main/java/com/example/platform/quota/app/QuotaService.java",
            "render-module/src/main/java/com/example/platform/render/infrastructure/queue/UsageRecordRepository.java",
            "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecisionEngine.java",
            "render-module/src/main/java/com/example/platform/render/infrastructure/billing/policy/PricingEngine.java",
            "render-module/src/main/java/com/example/platform/render/infrastructure/billing/policy/CreditSystem.java",
            "render-module/src/main/java/com/example/platform/render/infrastructure/billing/BillingEnforcementService.java",
            "render-module/src/main/java/com/example/platform/render/infrastructure/billing/RenderBillingRecordRepository.java",
            "billing-module/src/main/java/com/example/platform/billing/infrastructure/SubscriptionContractRepository.java",
            "payment-module/src/main/java/com/example/platform/payment/app/CheckoutPaymentBindingRegistry.java",
            "billing-module/src/main/java/com/example/platform/billing/domain/PaymentLedgerEntry.java");

    @Test
    void deprecatedQuotaModuleHasNoBuildOrRuntimePresence() {
        Path root = repositoryRoot();
        assertFalse(Files.exists(root.resolve("quota-billing-module")));
        for (String buildPath : List.of(
                "settings.gradle.kts", "platform-app/build.gradle.kts", "Dockerfile",
                "Dockerfile.optimized",
                "platform-app/src/main/java/com/example/platform/PlatformApplication.java")) {
            assertFalse(read(root.resolve(buildPath)).contains("quota-billing-module")
                            || read(root.resolve(buildPath)).contains("com.example.platform.quota"),
                    () -> "deprecated quota module remains referenced by " + buildPath);
        }
    }

    @Test
    void everyAcceptedDeleteShadowIsAbsent() {
        Path root = repositoryRoot();
        for (String path : ACCEPTED_DELETE_SHADOW_PATHS) {
            assertFalse(Files.exists(root.resolve(path)), () -> "accepted shadow still present: " + path);
        }
    }

    @Test
    void renderHasNoLocalCommercialDecisionOrPolicyAuthority() {
        Path root = repositoryRoot();
        assertFalse(Files.exists(root.resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/billing")));
        String submission = read(root.resolve(
                "render-module/src/main/java/com/example/platform/render/app/RenderJobSubmissionService.java"));
        String lifecycle = read(root.resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/unified/RequestLifecycleEngine.java"));
        assertFalse(submission.contains("BillingDecisionEngine") || submission.contains("RenderQuotaService"));
        assertTrue(submission.contains("CommercialAdmissionPort"));
        assertFalse(lifecycle.contains("BillingDecisionEngine") || lifecycle.contains("BillingDecisionRequest"));
        assertTrue(lifecycle.contains("CommercialDecision"));
    }

    @Test
    void renderConsumesOnlyNeutralCommercialContracts() {
        Path root = repositoryRoot();
        assertFalse(Files.exists(root.resolve(
                "render-module/src/main/java/com/example/platform/render/app/RenderJobValidationService.java")));
        String build = read(root.resolve("render-module/build.gradle.kts"));
        assertFalse(build.contains("project(\":billing-module\")"));
        assertFalse(build.contains("project(\":entitlement-module\")"));
        Path main = root.resolve("render-module/src/main/java");
        try (Stream<Path> paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = read(path);
                assertFalse(source.matches("(?sm).*^import com\\.example\\.platform\\.(billing|entitlement|payment|commerce|quota)\\..*"),
                        () -> "Render imports H5 authority instead of a neutral contract: " + path);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Test
    void renderDoesNotTurnCommercialAdmissionOrProviderCostIntoCustomerPrice() {
        Path root = repositoryRoot();
        String narrative = read(root.resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/semantic/NarrativeEngine.java"));
        String semanticNode = read(root.resolve(
                "render-module/src/main/java/com/example/platform/render/infrastructure/semantic/SemanticNode.java"));
        assertFalse(narrative.contains("commercialNode.getDoubleData(\"estimatedCost\""));
        assertFalse(narrative.contains("Estimated from the commercial decision"));
        assertFalse(semanticNode.contains("\"estimatedCost\""));
    }

    @Test
    void billingDoesNotMaintainAPeerPaymentLedger() {
        Path root = repositoryRoot();
        String reconciliation = read(root.resolve(
                "billing-module/src/main/java/com/example/platform/billing/app/ReconciliationService.java"));
        assertFalse(reconciliation.contains("paymentLedger"));
        assertFalse(reconciliation.contains("addPaymentEntry"));
        assertFalse(reconciliation.contains("PaymentLedgerEntry"));
    }

    @Test
    void frontendMoneyProjectionIsRetainedAndExplicitlyReclassified() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve(
                "federation-query-module/src/main/java/com/example/platform/federation/graphql/dto/MoneyDto.java")));
        String inventory = read(root.resolve(
                "docs/architecture/governance/billing-entitlement-payment-authority-convergence-repository-inventory-v1.json"));
        assertTrue(inventory.contains("NON_AUTHORITATIVE_FRONTEND_PROJECTION_OUT_OF_SCOPE"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts not found");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
