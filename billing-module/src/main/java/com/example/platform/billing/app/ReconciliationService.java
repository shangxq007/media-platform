package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.events.ReconciliationCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for running reconciliation between internal cost records and external invoices.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ConcurrentHashMap<String, ReconciliationRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ThirdPartyInvoiceImport> importedInvoices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReconciliationDifference> differences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CostLedgerEntry> costLedger = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PaymentLedgerEntry> paymentLedger = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    public ReconciliationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Import a third-party invoice (CSV/JSON simulation).
     */
    public ThirdPartyInvoiceImport importInvoice(String providerCode, String invoiceId,
            String tenantId, double amount, String currency, String description,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, String rawData) {
        ThirdPartyInvoiceImport importRecord = ThirdPartyInvoiceImport.create(
                providerCode, invoiceId, tenantId, amount, currency,
                description, periodStart, periodEnd, rawData);
        importedInvoices.put(importRecord.importId(), importRecord);
        log.info("ReconciliationService: imported invoice {} from provider={} amount={} {}",
                invoiceId, providerCode, amount, currency);
        return importRecord;
    }

    /**
     * Run reconciliation for a given period and source.
     */
    public ReconciliationRun runReconciliation(String sourceType, String sourceName,
            OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        ReconciliationRun run = ReconciliationRun.start(sourceType, sourceName, periodStart, periodEnd);
        runs.put(run.runId(), run);

        log.info("ReconciliationService: starting reconciliation run={} source={} period={}-{}",
                run.runId(), sourceName, periodStart, periodEnd);

        // Gather internal cost records for the period
        List<CostLedgerEntry> internalRecords = costLedger.values().stream()
                .filter(e -> !e.recordedAt().isBefore(periodStart) && !e.recordedAt().isAfter(periodEnd))
                .toList();

        // Gather imported external records
        List<ThirdPartyInvoiceImport> externalRecords = importedInvoices.values().stream()
                .filter(i -> !i.servicePeriodStart().isBefore(periodStart) && !i.servicePeriodEnd().isAfter(periodEnd))
                .toList();

        int matched = 0;
        int diffCount = 0;

        // Match records
        for (ThirdPartyInvoiceImport external : externalRecords) {
            Optional<CostLedgerEntry> match = internalRecords.stream()
                    .filter(i -> i.tenantId().equals(external.tenantId()))
                    .filter(i -> Math.abs(i.actualCost() - external.amount()) < 0.01)
                    .findFirst();

            if (match.isPresent()) {
                matched++;
            } else {
                // Record difference
                ReconciliationDifference diff = new ReconciliationDifference(
                        java.util.UUID.randomUUID().toString(),
                        run.runId(), external.tenantId(), "INVOICE",
                        null, external.importId(),
                        0.0, external.amount(), external.amount(),
                        external.currency(), ReconciliationDifference.STATUS_NEEDS_REVIEW,
                        "No matching internal record found",
                        OffsetDateTime.now(), null);
                differences.put(diff.differenceId(), diff);
                diffCount++;
            }
        }

        // Check for internal records without external match
        for (CostLedgerEntry internal : internalRecords) {
            boolean hasExternalMatch = externalRecords.stream()
                    .anyMatch(e -> e.tenantId().equals(internal.tenantId())
                            && Math.abs(e.amount() - internal.actualCost()) < 0.01);
            if (!hasExternalMatch) {
                ReconciliationDifference diff = new ReconciliationDifference(
                        java.util.UUID.randomUUID().toString(),
                        run.runId(), internal.tenantId(), "COST_RECORD",
                        internal.entryId(), null,
                        internal.actualCost(), 0.0, internal.actualCost(),
                        internal.currency(), ReconciliationDifference.STATUS_NEEDS_REVIEW,
                        "No matching external invoice found",
                        OffsetDateTime.now(), null);
                differences.put(diff.differenceId(), diff);
                diffCount++;
            }
        }

        Map<String, Object> summary = Map.of(
                "internalRecords", internalRecords.size(),
                "externalRecords", externalRecords.size(),
                "matched", matched,
                "differences", diffCount);

        ReconciliationRun completed = run.complete(
                internalRecords.size() + externalRecords.size(), matched, diffCount, summary);
        runs.put(run.runId(), completed);

        // Publish event (audit module listens and records)
        eventPublisher.publishEvent(new ReconciliationCompletedEvent(
                run.runId(), sourceType,
                internalRecords.size() + externalRecords.size(),
                matched, diffCount, "COMPLETED",
                summary, java.time.Instant.now()));

        log.info("ReconciliationService: completed run={} matched={} diffs={}",
                run.runId(), matched, diffCount);
        return completed;
    }

    /**
     * Add a cost ledger entry (called by cost finalization).
     */
    public void addCostEntry(CostLedgerEntry entry) {
        costLedger.put(entry.entryId(), entry);
    }

    /**
     * Add a payment ledger entry.
     */
    public void addPaymentEntry(PaymentLedgerEntry entry) {
        paymentLedger.put(entry.entryId(), entry);
    }

    /**
     * Resolve a difference.
     */
    public ReconciliationDifference resolveDifference(String differenceId, String status, String resolution) {
        ReconciliationDifference existing = differences.get(differenceId);
        if (existing == null) return null;

        ReconciliationDifference resolved = switch (status) {
            case ReconciliationDifference.STATUS_ACCEPTED -> existing.accept(resolution);
            case ReconciliationDifference.STATUS_REJECTED -> existing.reject(resolution);
            default -> existing.markForReview(resolution);
        };

        differences.put(differenceId, resolved);
        return resolved;
    }

    public List<ReconciliationDifference> getDifferences(String runId) {
        return differences.values().stream()
                .filter(d -> d.runId().equals(runId))
                .toList();
    }

    public ReconciliationRun getRun(String runId) {
        return runs.get(runId);
    }

    public List<ReconciliationRun> getAllRuns() {
        return runs.values().stream()
                .sorted(Comparator.comparing(ReconciliationRun::startedAt).reversed())
                .toList();
    }
}
