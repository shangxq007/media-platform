package com.example.platform.web;

import com.example.platform.billing.app.BillingLedgerService;
import com.example.platform.billing.app.CreditWalletService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.commerce.app.CommerceCatalogService;
import com.example.platform.commerce.domain.CanonicalProduct;
import com.example.platform.commerce.domain.ProductLineType;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/billing/me")
public class MeBillingController {

    private final SubscriptionBillingService subscriptionBillingService;
    private final CreditWalletService creditWalletService;
    private final BillingLedgerService billingLedgerService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final CommerceCatalogService commerceCatalogService;

    public MeBillingController(SubscriptionBillingService subscriptionBillingService,
                                 CreditWalletService creditWalletService,
                                 BillingLedgerService billingLedgerService,
                                 EntitlementPolicyService entitlementPolicyService,
                                 CommerceCatalogService commerceCatalogService) {
        this.subscriptionBillingService = subscriptionBillingService;
        this.creditWalletService = creditWalletService;
        this.billingLedgerService = billingLedgerService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.commerceCatalogService = commerceCatalogService;
    }

    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> getCurrentPlan(HttpServletRequest req) {
        BillingSubject subject = resolveSubject(req);
        SubscriptionContract base = subscriptionBillingService.getCurrentSubscription(
                subject.tenantId(), subject.userId());
        String tier = entitlementPolicyService.getTier(subject.tenantId());

        Map<String, Object> plan = new LinkedHashMap<>();
        if (base != null) {
            SubscriptionPlan billingPlan = subscriptionBillingService.getPlan(base.planKey());
            plan.put("planId", base.planKey());
            plan.put("name", billingPlan != null ? billingPlan.name() : base.planKey());
            plan.put("tier", tier);
            plan.put("description", billingPlan != null ? billingPlan.description() : "");
            plan.put("monthlyPrice", billingPlan != null ? billingPlan.basePriceMinor() / 100.0 : 0.0);
            plan.put("annualPrice", billingPlan != null ? billingPlan.basePriceMinor() * 12 / 100.0 : 0.0);
            plan.put("currency", billingPlan != null ? billingPlan.currencyCode() : "USD");
            plan.put("includedQuota", toPlanQuota(
                    subscriptionBillingService.getEffectiveIncludedQuota(subject.tenantId(), subject.userId())));
            plan.put("features", List.of());
            plan.put("isActive", base.isActiveAt(Instant.now()));
            plan.put("periodEndAt", base.periodEndAt());
        } else {
            plan.put("planId", "free");
            plan.put("name", "Free");
            plan.put("tier", tier);
            plan.put("description", "No active subscription");
            plan.put("monthlyPrice", 0.0);
            plan.put("annualPrice", 0.0);
            plan.put("currency", "USD");
            plan.put("includedQuota", Map.of());
            plan.put("features", List.of());
            plan.put("isActive", "FREE".equalsIgnoreCase(tier));
        }
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<Map<String, Object>>> listSubscriptions(HttpServletRequest req) {
        BillingSubject subject = resolveSubject(req);
        List<Map<String, Object>> items = subscriptionBillingService
                .listActiveSubscriptions(subject.tenantId(), subject.userId()).stream()
                .map(this::subscriptionToMap)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/effective-quota")
    public ResponseEntity<Map<String, Long>> effectiveQuota(HttpServletRequest req) {
        BillingSubject subject = resolveSubject(req);
        return ResponseEntity.ok(subscriptionBillingService.getEffectiveIncludedQuota(
                subject.tenantId(), subject.userId()));
    }

    @GetMapping("/credits")
    public ResponseEntity<Map<String, Object>> getCredits(HttpServletRequest req) {
        BillingSubject subject = resolveSubject(req);
        CreditWallet wallet = creditWalletService.getWalletByTenant(subject.tenantId(), subject.userId());
        if (wallet == null) {
            return ResponseEntity.ok(Map.of(
                    "walletId", "",
                    "balanceMinor", 0,
                    "balance", 0.0,
                    "currency", "USD",
                    "status", "NONE"));
        }
        return ResponseEntity.ok(Map.of(
                "walletId", wallet.walletId(),
                "balanceMinor", wallet.balanceMinor(),
                "balance", wallet.balanceMinor() / 100.0,
                "currency", wallet.currencyCode(),
                "status", wallet.status()));
    }

    @GetMapping("/credits/transactions")
    public ResponseEntity<Map<String, Object>> creditTransactions(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BillingSubject subject = resolveSubject(req);
        CreditWallet wallet = creditWalletService.getWalletByTenant(subject.tenantId(), subject.userId());
        List<Map<String, Object>> txns = List.of();
        if (wallet != null) {
            List<CreditTransaction> all = creditWalletService.getTransactions(wallet.walletId());
            int start = Math.min(page * size, all.size());
            int end = Math.min(start + size, all.size());
            txns = all.subList(start, end).stream().map(this::transactionToMap).toList();
        }
        return ResponseEntity.ok(Map.of("transactions", txns, "total", txns.size(), "page", page, "size", size));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> billingHistory(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BillingSubject subject = resolveSubject(req);
        List<Map<String, Object>> all = billingLedgerService.getLedger(subject.tenantId()).stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("entryId", entry.entryId());
                    row.put("type", entry.entryType());
                    row.put("amountMinor", entry.amountMinor());
                    row.put("amount", entry.amountMinor() / 100.0);
                    row.put("currency", entry.currencyCode());
                    row.put("status", "COMPLETED");
                    row.put("description", entry.description());
                    row.put("createdAt", entry.createdAt().toString());
                    return row;
                })
                .toList();
        int start = Math.min(page * size, all.size());
        int end = Math.min(start + size, all.size());
        return ResponseEntity.ok(Map.of(
                "entries", all.subList(start, end),
                "total", all.size(),
                "page", page,
                "size", size));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<Map<String, Object>>> invoices() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<Map<String, Object>> invoice(@PathVariable String invoiceId) {
        return ResponseEntity.ok(Map.of("invoiceId", invoiceId, "status", "STUB"));
    }

    @GetMapping("/upgrades")
    public ResponseEntity<List<Map<String, Object>>> upgrades(HttpServletRequest req) {
        BillingSubject subject = resolveSubject(req);
        String currentTier = entitlementPolicyService.getTier(subject.tenantId());
        List<Map<String, Object>> options = commerceCatalogService.listProducts().stream()
                .filter(p -> p.lineType() == ProductLineType.BASE_SUBSCRIPTION)
                .filter(p -> p.tierKey() != null && tierRank(p.tierKey()) > tierRank(currentTier))
                .map(p -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("targetTier", p.tierKey());
                    option.put("targetPlanId", p.planKey());
                    option.put("targetPlanName", p.displayName());
                    option.put("productCode", p.productCode());
                    option.put("monthlyPrice", p.priceMinor() / 100.0);
                    option.put("annualPrice", p.priceMinor() * 12 / 100.0);
                    option.put("currency", p.currencyCode());
                    option.put("additionalFeatures", List.of());
                    option.put("additionalQuota", Map.of());
                    option.put("recommended", "PRO".equals(p.tierKey()));
                    return option;
                })
                .toList();
        return ResponseEntity.ok(options);
    }

    @PostMapping("/credits/topup")
    public ResponseEntity<Map<String, Object>> topUpCredits(
            HttpServletRequest req,
            @RequestBody Map<String, Object> body) {
        BillingSubject subject = resolveSubject(req);
        long amountMinor = body.get("amount") instanceof Number n
                ? Math.round(n.doubleValue() * 100)
                : 0L;
        if (amountMinor <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "amount must be positive"));
        }
        CreditWallet wallet = creditWalletService.getWalletByTenant(subject.tenantId(), subject.userId());
        if (wallet == null) {
            wallet = creditWalletService.createWallet(subject.tenantId(), null, subject.userId(), "USD");
        }
        CreditWallet updated = creditWalletService.credit(
                wallet.walletId(), amountMinor, "TOP_UP", "me-topup", "Manual top-up");
        billingLedgerService.writeEntry(subject.tenantId(), null, subject.userId(),
                "CREDIT", amountMinor, updated.currencyCode(), "TOP_UP", "me-topup", "Credit top-up");
        return ResponseEntity.ok(Map.of(
                "transactionId", "topup-" + Instant.now().toEpochMilli(),
                "walletId", updated.walletId(),
                "amount", amountMinor / 100.0,
                "balanceAfter", updated.balanceMinor() / 100.0,
                "type", "TOP_UP",
                "createdAt", Instant.now().toString()));
    }

    private Map<String, Object> subscriptionToMap(SubscriptionContract contract) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("contractId", contract.contractId());
        map.put("planKey", contract.planKey());
        map.put("productCode", contract.productCode());
        map.put("contractRole", contract.contractRole().name());
        map.put("lifecycleState", contract.lifecycleState());
        map.put("periodStartAt", contract.periodStartAt());
        map.put("periodEndAt", contract.periodEndAt());
        map.put("basePriceMinor", contract.basePriceMinor());
        map.put("includedQuota", contract.includedQuota());
        return map;
    }

    private Map<String, Object> transactionToMap(CreditTransaction txn) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("transactionId", txn.transactionId());
        map.put("walletId", txn.walletId());
        map.put("type", txn.transactionType());
        map.put("amount", txn.amountMinor() / 100.0);
        map.put("balanceAfter", txn.balanceAfterMinor() / 100.0);
        map.put("description", txn.description());
        map.put("referenceId", txn.referenceId());
        map.put("createdAt", txn.createdAt().toString());
        return map;
    }

    private static Map<String, Object> toPlanQuota(Map<String, Long> meters) {
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("renderMinutes", meters.getOrDefault("render.minutes", 0L));
        quota.put("apiCalls", meters.getOrDefault("api.calls", 0L));
        quota.put("storageGb", meters.getOrDefault("storage.gb", 0L));
        quota.put("exports", meters.getOrDefault("exports", 0L));
        return quota;
    }

    private static int tierRank(String tier) {
        return switch (tier != null ? tier.toUpperCase() : "FREE") {
            case "PRO" -> 2;
            case "TEAM" -> 3;
            case "ENTERPRISE" -> 4;
            default -> 1;
        };
    }

    private BillingSubject resolveSubject(HttpServletRequest req) {
        Object subject = req.getAttribute("jwt.subject");
        String userId = subject != null ? subject.toString() : "anonymous";
        Object tenantAttr = req.getAttribute("jwt.tenantId");
        String tenantId = tenantAttr != null ? tenantAttr.toString() : TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "tenant-1";
        }
        if ("anonymous".equals(userId)) {
            userId = tenantId + "-billing-owner";
        }
        return new BillingSubject(tenantId, userId);
    }

    private record BillingSubject(String tenantId, String userId) {}
}
