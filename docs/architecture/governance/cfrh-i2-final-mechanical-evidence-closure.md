# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I2 FINAL MECHANICAL EVIDENCE CLOSURE

## Publication record

| Field | Value |
|---|---|
| BASE_SHA | 5318a3fd0477a92511ebb8dd1d56eaf6caa2ee41 |
| REVIEWED_PREDECESSOR_SHA | 4a5bb63770e7d0d06ccbaedd31ddcef708ce2977 |
| CORRECTION_SHA | fa17eff46180324c1744249da8910a9080e38da2 |
| PUBLICATION_SHA | f271f52ef86040f21f3632998c72c4182c36577e |

## Decisions

CFRH_I2_ARCHITECTURE_DIRECTION = UNCHANGED
CFRH_I2_EXECUTION_CONTRACT = UNCHANGED
CFRH_I2_CALLER_INVENTORY = UNCHANGED_CONTENT_MECHANICALLY_RECONCILED
CFRH_I2_ENDPOINT_MODEL = UNCHANGED
CFRH_I2_BASEJOB_OWNERSHIP_CONTRACT = UNCHANGED
CFRH_I2_SYSTEM_AUTHORITY_MODEL = UNCHANGED
CFRH_I2_PRODUCTION_IMPLEMENTATION = NOT_STARTED
ARCHITECTURE_PREMISE_FAILURE = NO
ARCHITECTURE_ESCALATION = NONE
BLOCKERS = 0
UNRESOLVED_DISPOSITION_COUNT = 0
READY_FOR_CHATGPT_CFRH_I2_FINAL_MECHANICAL_REVIEW = YES

## Corrections applied (F1..F4)

F1 — REAL production source reconciliation: validator now scans actual
     src/main/java sources (platform-app + render-module) for TRQ invocation
     expressions, builds SOURCE_INVOCATION_SITE_SET (class, method, line,
     callee), and computes exact two-way closure vs the authoritative caller
     ledger. Caller TSV caller_symbol corrected to real source method names
     (snapshot→revisionSnapshot, mergeDiff→conflicts) so source==ledger is
     exact. caller_line restricted to ^[0-9]+$.

F2 — COMPUTED vs EXPECTED separation: every metric compared as
     computed_variable == expected_constant; publication values parsed and
     compared against computed variables, not duplicated literal expectations.

F3 — REAL cross-ledger closure: behavior→caller validity, caller→ownership
     reconciliation, P1 exact expected 8-symbol set (missing/extra=0),
     ownership manifest classification counts, endpoint coherence
     (effective = direct OR transitive; restore canonical/NO/YES/YES),
     orphan caller/symbol = 0.

F4 — Authoritative doc single truth: stale current-state claims (19 callers,
     14 sites, 11 query endpoints) removed or explicitly marked
     HISTORICAL/REJECTED/SUPERSEDED/PREDECESSOR_METRIC.

## Machine-readable metrics (computed from ledgers/source by validator)

caller_inventory_row_count = 22
source_invocation_site_count = 22
ledger_invocation_site_count = 22
unique_caller_method_count = 18
missing_ledger_site_count = 0
extra_ledger_site_count = 0
duplicate_ledger_site_count = 0
unresolved_caller_disposition_count = 0

http_endpoint_count = 15
direct_legacy_query_endpoint_count = 10
transitive_only_legacy_query_endpoint_count = 1
effective_legacy_query_dependency_endpoint_count = 11
unresolved_endpoint_count = 0

ownership_surface_count = 19
p1_symbol_count = 8
forbidden_ambient_global_count = 5
tenant_unverified_count = 6
load_then_check_count = 5
system_authority_exception_count = 1
already_safe_count = 1
safe_keep_count = 1
orphan_caller_count = 0
orphan_symbol_count = 0

p1_expected_symbol_count = 8
p1_actual_symbol_count = 8
p1_missing_symbol_count = 0
p1_extra_symbol_count = 0

unknown_caller_behavior_count = 0

## Validation

- verify-cfrh-i2-inventory-contract.py: mechanical M/M PASS from committed tree
- targeted RED suite: RED-A source substitution, RED-B publication mismatch,
  RED-C P1 row removal, RED-D compressed caller_line, RED-E endpoint
  contradiction, RED-F unknown behavior — all FAIL-DETECTED
- final GREEN from exact committed publication tree

READY_FOR_CHATGPT_CFRH_I2_FINAL_MECHANICAL_REVIEW
