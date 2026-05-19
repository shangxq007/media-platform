package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingLedgerService {

    private static final Logger log = LoggerFactory.getLogger(BillingLedgerService.class);

    private final ConcurrentHashMap<String, BillingLedgerEntry> ledger = new ConcurrentHashMap<>();

    public BillingLedgerEntry writeEntry(String tenantId, String workspaceId, String userId,
                                          String entryType, long amountMinor, String currencyCode,
                                          String referenceType, String referenceId,
                                          String description) {
        String entryId = Ids.newId("ble");
        BillingLedgerEntry entry = new BillingLedgerEntry(
                entryId, tenantId, workspaceId, userId,
                entryType, amountMinor, currencyCode,
                referenceType, referenceId, description, Instant.now());
        ledger.put(entryId, entry);
        log.info("BillingLedgerService: wrote entry {} type={} amount={} {}",
                entryId, entryType, amountMinor, currencyCode);
        return entry;
    }

    public BillingLedgerEntry getEntry(String entryId) {
        return ledger.get(entryId);
    }

    public List<BillingLedgerEntry> getLedger(String tenantId) {
        return ledger.values().stream()
                .filter(e -> tenantId.equals(e.tenantId()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    public List<BillingLedgerEntry> getLedgerByTenantAndType(String tenantId, String entryType) {
        return ledger.values().stream()
                .filter(e -> tenantId.equals(e.tenantId()))
                .filter(e -> entryType.equals(e.entryType()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    public long getBalance(String tenantId) {
        return ledger.values().stream()
                .filter(e -> tenantId.equals(e.tenantId()))
                .mapToLong(e -> {
                    if (e.entryType().equals(BillingLedgerEntry.TYPE_REFUND)
                            || e.entryType().equals(BillingLedgerEntry.TYPE_CREDIT)
                            || e.entryType().equals(BillingLedgerEntry.TYPE_DISCOUNT)) {
                        return -e.amountMinor();
                    }
                    return e.amountMinor();
                })
                .sum();
    }
}
