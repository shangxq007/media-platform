# Reconciliation Runbook

## Overview

The reconciliation system compares internal cost records with external third-party invoices to identify discrepancies. It supports importing simulated bills (CSV/JSON) and generating difference reports.

## Workflow

1. **Import** - Import third-party invoice data
2. **Match** - Compare with internal `CostLedgerEntry` records
3. **Diff** - Identify unmatched records
4. **Resolve** - Mark differences as ACCEPTED, REJECTED, or NEEDS_REVIEW
5. **Audit** - Record reconciliation run in audit trail

## Data Model

| Entity | Purpose |
|--------|---------|
| `ReconciliationRun` | A single reconciliation execution |
| `ThirdPartyInvoiceImport` | Imported external invoice |
| `CostLedgerEntry` | Internal cost record |
| `PaymentLedgerEntry` | Internal payment record |
| `ReconciliationDifference` | Identified discrepancy |

## Difference Status

| Status | Description |
|--------|-------------|
| `NEEDS_REVIEW` | Automatically assigned on detection |
| `ACCEPTED` | Difference is within tolerance |
| `REJECTED` | Difference is invalid |
| `NEEDS_REVIEW` | Requires manual investigation |

## Current Limitations

- **Simulated invoices only** - Real provider API integration is future work
- **Simple matching** - Matches on tenant ID and amount within $0.01 tolerance
- **No partial matching** - Does not support split invoices or aggregated line items

## API Usage

```java
// Import an invoice
ThirdPartyInvoiceImport imported = reconciliationService.importInvoice(
    "aws", "inv-001", "tenant-1", 50.0, "USD",
    "Compute charges", periodStart, periodEnd, rawData);

// Run reconciliation
ReconciliationRun run = reconciliationService.runReconciliation(
    "EXTERNAL_CSV", "aws-invoice", periodStart, periodEnd);

// Review differences
List<ReconciliationDifference> diffs = reconciliationService.getDifferences(run.runId());
for (ReconciliationDifference diff : diffs) {
    reconciliationService.resolveDifference(diff.differenceId(), "ACCEPTED", "Within tolerance");
}
```

## Error Codes

| Code | Description |
|------|-------------|
| `RECON-409-001` | Reconciliation difference found |
