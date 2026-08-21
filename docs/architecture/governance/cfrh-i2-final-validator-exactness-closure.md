# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I2 FINAL VALIDATOR EXACTNESS CLOSURE

## Publication record

| Field | Value |
|---|---|
| BASE_SHA | 5318a3fd0477a92511ebb8dd1d56eaf6caa2ee41 |
| PREDECESSOR_SHA | a522313caf71e768c6f8e09a31d4af59f5867e8c |
| CORRECTION_SHA | 4ab4fb5a0dff2e70c47010b9a9d34c0d5552292f |
| PUBLICATION_SHA | (set after commit) |

## Decisions (all UNCHANGED)

ARCHITECTURE_DIRECTION = UNCHANGED
EXECUTION_CONTRACT = UNCHANGED
CALLER_LEDGER_CONTENT = UNCHANGED
RESTORE_MODEL = UNCHANGED
BASEJOB_OWNERSHIP = UNCHANGED
SYSTEM_AUTHORITY_MODEL = UNCHANGED
I2_A_G_SEQUENCING = UNCHANGED
I2_C1_C15 = UNCHANGED
PRODUCTION_IMPLEMENTATION = NOT_STARTED
BLOCKERS = 0
UNRESOLVED_DISPOSITION_COUNT = 0
ARCHITECTURE_PREMISE_FAILURE = NO
ARCHITECTURE_ESCALATION = NONE
READY_FOR_CHATGPT_CFRH_I2_FINAL_VALIDATOR_REVIEW = YES

NOTE: CFRH_I2_DECISION_RECOVERY = CLOSED and
READY_FOR_CFRH_I2_BOUNDED_IMPLEMENTATION = YES remain reserved for ChatGPT
after independent review. Not self-authorized here.

## Corrections applied

F1 — FULL 4-TUPLE SOURCE↔LEDGER EXACT IDENTITY
- 3-tuple fallback (class, method, line) DELETED.
- SOURCE_INVOCATION_SITE_SET == LEDGER_INVOCATION_SITE_SET as exact
  (caller_class, caller_method, source_line, callee_symbol) tuples.
- behavior_id ↔ callee_symbol enforced through frozen TRQ map (F1-09).

F2 — REPOSITORY-SYMBOL / OWNERSHIP / P1 CROSS-LEDGER CLOSURE
- repository-read-symbol-inventory.tsv (12 symbols) + ownership-read-manifest.tsv
  (10 rows) loaded and reconciled.
- Repository symbols classified: manifest-governed (A), SAFE KEEP (B),
  system-authority (C), internal TRQ-support (D), orphan (E).
- orphan_repository_symbol_count and orphan_ownership_symbol_count computed
  separately from p1_extra_symbol_count (distinct metrics).

F3 — AUTHORITATIVE-DOC STALE-PATTERN ENFORCEMENT
- Explicit doc set: execution plan, decision-recovery doc, inventory-correction
  pub, mechanical-evidence pub, this pub.
- Four stale patterns scanned: 19-caller, 14-site, ambiguous 11-query-endpoints,
  unmarked PRODUCTION_CALLER_COUNT=19. All require HISTORICAL/REJECTED/
  SUPERSEDED/PREDECESSOR marker in local context.
- Execution plan endpoint taxonomy made explicit (15 total / 10 direct /
  1 transitive-only restore / 11 effective).

F4 — EXACT REQUIRED PUBLICATION METRIC SET
- checked >= N replaced by explicit REQUIRED_PUBLICATION_METRIC_SET (37 metrics).
- Every required metric must be present and equal to its computed value.

## Machine-readable metrics (computed from ledgers/source by validator)

caller_inventory_row_count = 22
source_invocation_site_count = 22
ledger_invocation_site_count = 22
unique_caller_method_count = 18
missing_ledger_site_count = 0
extra_ledger_site_count = 0
duplicate_ledger_site_count = 0
unresolved_caller_disposition_count = 0
behavior_callee_mismatch_count = 0
invalid_caller_line_count = 0

http_endpoint_count = 15
direct_legacy_query_endpoint_count = 10
transitive_only_legacy_query_endpoint_count = 1
effective_legacy_query_dependency_endpoint_count = 11
unresolved_endpoint_count = 0
endpoint_contradiction_count = 0

ownership_surface_count = 19

p1_symbol_count = 8
p1_expected_symbol_count = 8
p1_actual_symbol_count = 8
p1_missing_symbol_count = 0
p1_extra_symbol_count = 0

forbidden_ambient_global_count = 5
tenant_unverified_count = 6
load_then_check_count = 5
system_authority_exception_count = 1
already_safe_count = 1
safe_keep_count = 1

repository_symbol_count = 12
governed_repository_symbol_count = 12
orphan_repository_symbol_count = 0
orphan_ownership_symbol_count = 0
orphan_caller_count = 0
unknown_caller_behavior_count = 0

stale_19_caller_claim_count = 0
stale_14_site_claim_count = 0
ambiguous_11_query_endpoint_claim_count = 0

## Validation

- verify-cfrh-i2-inventory-contract.py: mechanical M/M PASS from committed tree
- fresh targeted RED-G..J (4/4 FAIL-DETECTED) + optional RED-K/L
- prior RED-A..F: previously accepted evidence, NOT rerun this round
- final GREEN from exact committed publication tree

READY_FOR_CHATGPT_CFRH_I2_FINAL_VALIDATOR_REVIEW
