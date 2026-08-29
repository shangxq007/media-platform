package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingLedgerService {

    private final BillingLedgerJdbcRepository repository;

    @Autowired
    public BillingLedgerService(Optional<BillingLedgerJdbcRepository> repository) {
        this.repository = repository.orElseThrow(() ->
                new IllegalStateException("durable Billing ledger repository is required"));
    }

    @Transactional
    public BillingLedgerEntry writeEntry(String tenantId, String workspaceId, String userId,
                                         String entryType, long amountMinor, String currencyCode,
                                         String referenceType, String referenceId,
                                         String description) {
        PrincipalRef principal = new PrincipalRef(tenantId, PrincipalType.USER, userId,
                workspaceId, null);
        Instant now = Instant.now();
        BillingLedgerEntry entry = new BillingLedgerEntry(Ids.newId("ble"), principal,
                entryType, new Money(amountMinor, currencyCode), referenceType, referenceId,
                description, "ledger:" + tenantId + ":" + referenceType + ":" + referenceId
                + ":" + entryType, null, now);
        return repository.append(entry);
    }

    @Transactional(readOnly = true)
    public BillingLedgerEntry getEntry(String tenantId, String entryId) {
        return repository.findByTenantAndId(tenantId, entryId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BillingLedgerEntry> getLedger(String tenantId) {
        return repository.findByTenant(tenantId);
    }

    @Transactional(readOnly = true)
    public List<BillingLedgerEntry> getLedgerByTenantAndType(String tenantId, String entryType) {
        return repository.findByTenantAndType(tenantId, entryType);
    }

    public long getBalance(String tenantId) {
        return getLedger(tenantId).stream().mapToLong(entry -> {
            if (entry.entryType().equals(BillingLedgerEntry.TYPE_REFUND)
                    || entry.entryType().equals(BillingLedgerEntry.TYPE_CREDIT)
                    || entry.entryType().equals(BillingLedgerEntry.TYPE_DISCOUNT)) {
                return Math.negateExact(entry.amountMinor());
            }
            return entry.amountMinor();
        }).reduce(0L, Math::addExact);
    }
}
