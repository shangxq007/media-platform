#!/usr/bin/env python3
"""Network-free guard for the bounded commercial authority contract V1.

Validates the contract, repository inventory/ledger, exact base, bounded changed
paths, evidence line anchors, frozen writer-candidate sets, and source drift
markers. ``--self-test`` applies in-memory mutations and succeeds only when
every negative control is rejected. It never modifies repository files.
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path


REPO = Path(__file__).resolve().parents[4]
GOV = REPO / "docs" / "architecture" / "governance"
CONTRACT = GOV / "billing-entitlement-payment-authority-convergence-bounded-architecture-contract-v1.md"
INVENTORY = GOV / "billing-entitlement-payment-authority-convergence-repository-inventory-v1.json"
GUARD = Path(__file__).resolve()
BASE_SHA = "e02579181ba3049ae65ed81080c93a7212f5833d"
BASE_TREE = "b67136e3a4b4e08688091bad0c4dad30d841978d"
ACCEPTED_CANDIDATE_SHA = "586be5a08e90482ddcda9530fb66bd7783637361"

ALLOWED_CHANGED_PATHS = {
    CONTRACT.relative_to(REPO).as_posix(),
    INVENTORY.relative_to(REPO).as_posix(),
    GUARD.relative_to(REPO).as_posix(),
}
DISPOSITIONS = {
    "REUSE_AS_CANONICAL",
    "REUSE_MECHANICS_ONLY",
    "MIGRATE_REDESIGN",
    "DELETE_SHADOW",
    "DEFER",
    "UNCLASSIFIED",
}
ENTRY_FIELDS = {
    "id", "path", "symbol", "current_role", "current_authority",
    "target_authority", "disposition", "rationale", "dependents",
    "migration_action", "guard_requirement", "evidence_lines",
}
SEPARATIONS = {
    "Payment != Billing != Subscription != Entitlement != Quota != Usage != Cost != Price",
    "ObservedRuntimeUsage != BillableUsage",
    "Quota != Capacity",
    "Quota != Reservation",
    "Price != ExecutionCost",
    "SubscriptionPlan != CapabilityContract",
    "Entitlement != RuntimeAvailability",
    "PaymentStatus != EntitlementAuthority",
}
REQUIRED_CONTRACT_TOKENS = {
    "ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1",
    "SINGLE_CANONICAL_QUOTA_USAGE_WRITER_V1",
    "MULTIPLE_CANONICAL_WRITER_CANDIDATES",
    "CapabilityExists && RuntimeAvailable && Entitled && PolicyAllowed && WithinQuota",
    "NOT_ENTITLED", "POLICY_DENIED", "QUOTA_EXCEEDED", "SUBSCRIPTION_INACTIVE",
    "COMMERCIAL_ACCOUNT_SUSPENDED", "BILLING_ACTION_REQUIRED", "PAYMENT_FAILED",
    "TRIAL_EXPIRED",
    "BILLING_ENTITLEMENT_PAYMENT_DECISION_RECOVERY=PASS",
    "COMMERCIAL_AUTHORITY_CONTRACT=ACCEPTED_WITH_BOUNDED_REFINEMENTS",
    "READY_FOR_COMMERCIAL_AUTHORITY_IMPLEMENTATION=YES",
    "H5_COMMERCIAL_AUTHORITY_IMPLEMENTATION_AUTHORIZATION=GO",
    "H5_INDEPENDENT_CHATGPT_ARCHITECTURE_REVIEW=PASS_WITH_BOUNDED_REFINEMENTS",
    "EFFECTIVE_CAPABILITY_VIEW_IS_DERIVED_APPLICATION_PROJECTION_V1",
    "ENTITLEMENT_AND_QUOTA_REMAIN_SEPARATE_AUTHORITIES_V1",
    "EXECUTION_COST_IS_NOT_COMMERCIAL_PRICE_AUTHORITY_V1",
    "CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT=0",
    "BLOCKERS=0",
}
WRITER_SETS = {
    "quota_usage": {"INV-002", "INV-005", "INV-009"},
    "subscription_state": {"INV-026", "INV-027", "INV-028"},
    "billing_invoice_state": {"INV-021", "INV-028", "INV-029"},
    "payment_transaction_state": {"INV-032", "INV-035", "INV-036"},
    "entitlement_grants": {"INV-037", "INV-038", "INV-039"},
}
TARGET_WRITERS = {
    "quota_usage": "INV-002",
    "subscription_state": "INV-026",
    "billing_invoice_state": "INV-029",
    "payment_transaction_state": "INV-032",
    "entitlement_grants": "INV-037",
}


def git(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args], cwd=REPO, text=True, capture_output=True, check=False
    )


def validate_contract(text: str) -> list[str]:
    errors: list[str] = []
    for token in sorted(SEPARATIONS | REQUIRED_CONTRACT_TOKENS):
        if token not in text:
            errors.append(f"contract missing token: {token}")

    clauses = [int(value) for value in re.findall(r"^### C(\d+) —", text, re.MULTILINE)]
    if clauses != list(range(1, 28)):
        errors.append(f"contract clauses are not exact ordered C1..C27: {clauses}")

    phases = [int(value) for value in re.findall(r"^\| I(\d+) \|", text, re.MULTILINE)]
    if phases != list(range(11)):
        errors.append(f"implementation phases are not exact ordered I0..I10: {phases}")

    forbidden_authorization = (
        "IMPLEMENTATION_AUTHORIZATION=NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE" in text
        or "EffectiveCapabilityView is owned by H5" in text
    )
    if forbidden_authorization:
        errors.append("contract retains superseded pre-acceptance authority text")
    return errors


def validate_inventory(
    data: dict, *, check_evidence: bool = True, check_drift: bool = True
) -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    metrics: dict[str, int] = {}

    if data.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    if data.get("base_sha") != BASE_SHA or data.get("base_tree") != BASE_TREE:
        errors.append("inventory base SHA/tree mismatch")
    if set(data.get("allowed_dispositions", [])) != DISPOSITIONS:
        errors.append("allowed_dispositions does not equal frozen enum")
    if set(data.get("required_entry_fields", [])) != ENTRY_FIELDS:
        errors.append("required_entry_fields does not equal frozen schema")

    entries = data.get("inventory")
    if not isinstance(entries, list) or not entries:
        return errors + ["inventory must be a non-empty list"], metrics

    ids: list[str] = []
    disposition_counts: Counter[str] = Counter()
    by_id: dict[str, dict] = {}
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"inventory[{index}] is not an object")
            continue
        missing = ENTRY_FIELDS - set(entry)
        if missing:
            errors.append(f"inventory[{index}] missing fields: {sorted(missing)}")
            continue
        entry_id = entry["id"]
        ids.append(entry_id)
        by_id[entry_id] = entry
        disposition = entry["disposition"]
        disposition_counts[disposition] += 1
        if not re.fullmatch(r"INV-\d{3}", str(entry_id)):
            errors.append(f"invalid inventory id: {entry_id}")
        if disposition not in DISPOSITIONS:
            errors.append(f"{entry_id}: invalid disposition {disposition}")
        for field in ENTRY_FIELDS - {"dependents", "evidence_lines"}:
            if entry.get(field) is None or str(entry.get(field)).strip() == "":
                errors.append(f"{entry_id}: blank required field {field}")
        if not isinstance(entry["dependents"], list) or not entry["dependents"]:
            errors.append(f"{entry_id}: dependents must be non-empty list")
        if not isinstance(entry["evidence_lines"], list) or not entry["evidence_lines"]:
            errors.append(f"{entry_id}: evidence_lines must be non-empty list")

        source = REPO / str(entry["path"])
        if not source.is_file():
            errors.append(f"{entry_id}: missing source path {entry['path']}")
            continue
        if check_evidence:
            lines = source.read_text(encoding="utf-8", errors="replace").splitlines()
            for evidence in entry["evidence_lines"]:
                if not isinstance(evidence, dict) or set(evidence) != {"start", "end", "contains"}:
                    errors.append(f"{entry_id}: malformed evidence line object")
                    continue
                start, end = evidence["start"], evidence["end"]
                if not isinstance(start, int) or not isinstance(end, int) or start < 1 or end < start or end > len(lines):
                    errors.append(f"{entry_id}: invalid evidence range {start}-{end}")
                    continue
                segment = "\n".join(lines[start - 1:end])
                if evidence["contains"] not in segment:
                    errors.append(
                        f"{entry_id}: evidence token absent at {entry['path']}:{start}-{end}: "
                        f"{evidence['contains']}"
                    )

    duplicate_count = len(ids) - len(set(ids))
    unclassified_count = disposition_counts["UNCLASSIFIED"]
    metrics["inventory_entry_count"] = len(entries)
    metrics["duplicate_inventory_id_count"] = duplicate_count
    metrics["unclassified_count"] = unclassified_count
    if duplicate_count:
        errors.append(f"duplicate inventory IDs: {duplicate_count}")
    if unclassified_count:
        errors.append(f"UNCLASSIFIED entries: {unclassified_count}")

    summary = data.get("summary", {})
    expected_summary = {
        "inventory_entry_count": len(entries),
        "duplicate_inventory_id_count": duplicate_count,
        "unclassified_count": unclassified_count,
    }
    for key, computed in expected_summary.items():
        if summary.get(key) != computed:
            errors.append(f"summary {key}={summary.get(key)} computed={computed}")
    declared_counts = summary.get("disposition_counts", {})
    for disposition in sorted(DISPOSITIONS):
        if declared_counts.get(disposition) != disposition_counts[disposition]:
            errors.append(
                f"disposition count {disposition}={declared_counts.get(disposition)} "
                f"computed={disposition_counts[disposition]}"
            )
    if sum(declared_counts.values()) != len(entries):
        errors.append("declared disposition counts do not sum to inventory size")

    writer_sets = data.get("writer_candidate_sets", [])
    if len(writer_sets) != 5:
        errors.append(f"writer candidate category count must be 5, got {len(writer_sets)}")
    seen_categories: set[str] = set()
    for writer_set in writer_sets:
        category = writer_set.get("category")
        seen_categories.add(category)
        if writer_set.get("status") != "MULTIPLE_CANONICAL_WRITER_CANDIDATES":
            errors.append(f"{category}: missing MULTIPLE_CANONICAL_WRITER_CANDIDATES status")
        candidates = writer_set.get("candidates", [])
        candidate_ids = {candidate.get("inventory_id") for candidate in candidates}
        if candidate_ids != WRITER_SETS.get(category, set()):
            errors.append(f"{category}: candidate set {sorted(candidate_ids)}")
        if len(candidates) < 2:
            errors.append(f"{category}: duplicate writers were not identified")
        targets = [candidate for candidate in candidates if candidate.get("target_canonical_writer") is True]
        if len(targets) != 1:
            errors.append(f"{category}: target canonical writer count={len(targets)}")
        elif targets[0].get("inventory_id") != TARGET_WRITERS.get(category):
            errors.append(f"{category}: wrong target canonical writer")
        if not str(writer_set.get("canonical_authority", "")).strip():
            errors.append(f"{category}: blank canonical_authority")
        for candidate_id in candidate_ids:
            if candidate_id not in by_id:
                errors.append(f"{category}: unknown inventory candidate {candidate_id}")
    if seen_categories != set(WRITER_SETS):
        errors.append(f"writer categories mismatch: {sorted(seen_categories)}")

    storage_tables = data.get("storage_tables", [])
    if not storage_tables:
        errors.append("storage_tables is empty")
    for table in storage_tables:
        path = REPO / str(table.get("path", ""))
        match = re.fullmatch(r"(\d+)-(\d+)", str(table.get("lines", "")))
        if not path.is_file() or not match:
            errors.append(f"invalid storage table evidence: {table}")
            continue
        line_count = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
        if int(match.group(1)) < 1 or int(match.group(2)) > line_count:
            errors.append(f"storage table evidence out of bounds: {table.get('table')}")

    if check_drift:
        assertions = data.get("source_drift_assertions", [])
        assertion_ids = [assertion.get("id") for assertion in assertions]
        if len(assertion_ids) != len(set(assertion_ids)) or len(assertions) < 10:
            errors.append("source drift assertions are missing or duplicated")
        for assertion in assertions:
            path = REPO / str(assertion.get("path", ""))
            if not path.is_file():
                errors.append(f"{assertion.get('id')}: drift path missing")
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            actual = text.count(str(assertion.get("pattern", "")))
            expected = assertion.get("expected_count")
            if actual != expected:
                errors.append(
                    f"{assertion.get('id')}: source drift count actual={actual} expected={expected}"
                )

    return errors, metrics


def repository_checks() -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    metrics: dict[str, int] = {}

    trailing_whitespace_lines = 0
    missing_final_newline_files = 0
    for artifact in [CONTRACT, INVENTORY, GUARD]:
        artifact_text = artifact.read_text(encoding="utf-8")
        trailing_whitespace_lines += sum(
            1 for line in artifact_text.splitlines() if line.rstrip(" \t") != line
        )
        if not artifact_text.endswith("\n"):
            missing_final_newline_files += 1
    metrics["artifact_trailing_whitespace_lines"] = trailing_whitespace_lines
    metrics["artifact_missing_final_newline_files"] = missing_final_newline_files
    if trailing_whitespace_lines:
        errors.append(f"new artifacts contain trailing whitespace lines={trailing_whitespace_lines}")
    if missing_final_newline_files:
        errors.append(f"new artifacts missing final newline files={missing_final_newline_files}")

    head = git("rev-parse", "HEAD").stdout.strip()
    origin = git("rev-parse", "origin/main").stdout.strip()
    base_tree = git("rev-parse", f"{BASE_SHA}^{{tree}}").stdout.strip()
    if base_tree != BASE_TREE:
        errors.append(f"base tree drift: {base_tree}")
    if origin != BASE_SHA:
        errors.append(f"origin/main drift: {origin}")

    # The refinement guard works on the independently accepted candidate and
    # on exactly one append-forward bounded-refinement commit above it.
    committed_refinement = head != ACCEPTED_CANDIDATE_SHA
    if committed_refinement:
        parent = git("rev-parse", "HEAD^").stdout.strip()
        commit_count = git("rev-list", "--count", f"{ACCEPTED_CANDIDATE_SHA}..HEAD").stdout.strip()
        if parent != ACCEPTED_CANDIDATE_SHA or commit_count != "1":
            errors.append(
                f"refinement history must be exactly one commit on accepted candidate: "
                f"head={head} parent={parent} count={commit_count}"
            )

    changed: set[str] = set()
    change_commands = [
        ("diff", "--name-only"),
        ("diff", "--cached", "--name-only"),
        ("ls-files", "--others", "--exclude-standard"),
    ]
    if committed_refinement:
        change_commands.append(("diff", "--name-only", f"{ACCEPTED_CANDIDATE_SHA}..HEAD"))
    for args in change_commands:
        result = git(*args)
        if result.returncode != 0:
            errors.append(f"git {' '.join(args)} failed: {result.stderr.strip()}")
        changed.update(line for line in result.stdout.splitlines() if line)
    out_of_scope = sorted(changed - ALLOWED_CHANGED_PATHS)
    if out_of_scope:
        errors.append(f"out-of-scope changed paths: {out_of_scope}")
    staged = git("diff", "--cached", "--name-only").stdout.splitlines()
    if staged:
        errors.append(f"staged changes are not allowed: {staged}")
    metrics["changed_path_count"] = len(changed)

    technical_files = [
        REPO / "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility/CompatibilityKernel.java",
        REPO / "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/RuntimeEligibilityEvaluator.java",
        REPO / "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/RuntimeEligibilityReason.java",
    ]
    commercial_import = re.compile(r"^import com\.example\.platform\.(billing|entitlement|payment|commerce|quota)\.", re.M)
    coupling_hits = sum(
        len(commercial_import.findall(path.read_text(encoding="utf-8")))
        for path in technical_files
    )
    metrics["technical_commercial_import_hits"] = coupling_hits
    if coupling_hits != 0:
        errors.append(f"H1 technical/commercial import coupling hits={coupling_hits}")

    render_import = re.compile(r"^import com\.example\.platform\.(billing|entitlement|quota)\.", re.M)
    render_import_hits = 0
    for path in (REPO / "render-module/src/main").rglob("*.java"):
        render_import_hits += len(render_import.findall(path.read_text(encoding="utf-8", errors="replace")))
    metrics["render_commercial_import_hits"] = render_import_hits
    if render_import_hits < 3:
        errors.append("expected Render cross-module commercial imports were not reproduced")

    forbidden_names = re.compile(r"ProTimeline|EnterpriseRenderGraph|FreeAudioMix|Capability\.proOnly")
    plan_capability_hits = 0
    for path in REPO.rglob("*.java"):
        relative = path.relative_to(REPO).as_posix()
        if "/src/main/" not in relative or "capabil" not in relative.lower():
            continue
        plan_capability_hits += len(forbidden_names.findall(path.read_text(encoding="utf-8", errors="replace")))
    metrics["plan_name_in_capability_hits"] = plan_capability_hits
    if plan_capability_hits:
        errors.append(f"plan-specific capability symbols found={plan_capability_hits}")

    effective_hits = 0
    for path in REPO.rglob("*.java"):
        relative = path.relative_to(REPO).as_posix()
        if "/src/main/" in relative:
            effective_hits += path.read_text(encoding="utf-8", errors="replace").count("EffectiveCapabilityView")
    metrics["existing_effective_capability_view_hits"] = effective_hits
    if effective_hits != 0:
        errors.append(f"base inventory expected no EffectiveCapabilityView, found {effective_hits}")

    raw_payload_paths: set[str] = set()
    field_pattern = re.compile(r"\bString\s+(?:rawPayload|payload)\b")
    for base in [REPO / "payment-module/src/main/java/com/example/platform/payment/domain",
                 REPO / "payment-module/src/main/java/com/example/platform/payment/api/dto"]:
        for path in base.rglob("*.java"):
            if field_pattern.search(path.read_text(encoding="utf-8", errors="replace")):
                raw_payload_paths.add(path.relative_to(REPO).as_posix())
    expected_payload_paths = {
        "payment-module/src/main/java/com/example/platform/payment/domain/VerifyPaymentCommand.java",
        "payment-module/src/main/java/com/example/platform/payment/api/dto/ConfirmPaymentRequest.java",
    }
    metrics["raw_provider_payload_leak_paths"] = len(raw_payload_paths)
    if raw_payload_paths != expected_payload_paths:
        errors.append(f"raw provider payload leak set drift: {sorted(raw_payload_paths)}")

    money_pattern = re.compile(
        r"\b(?:double|Double)\s+(?:amount|price|cost|balance|estimatedCost|actualCost|finalPrice|computeCost|storageCost|apiCost)\w*"
    )
    money_hits = 0
    money_paths: set[str] = set()
    for module in ["billing-module", "payment-module", "commerce-module", "render-module", "federation-query-module"]:
        for path in (REPO / module / "src/main").rglob("*.java"):
            count = len(money_pattern.findall(path.read_text(encoding="utf-8", errors="replace")))
            if count:
                money_hits += count
                money_paths.add(path.relative_to(REPO).as_posix())
    required_unsafe_money_paths = {
        "render-module/src/main/java/com/example/platform/render/infrastructure/billing/policy/PricingEngine.java",
        "render-module/src/main/java/com/example/platform/render/infrastructure/billing/policy/CreditSystem.java",
        "render-module/src/main/java/com/example/platform/render/infrastructure/billing/RenderBillingRecord.java",
        "billing-module/src/main/java/com/example/platform/billing/domain/PaymentLedgerEntry.java",
        "federation-query-module/src/main/java/com/example/platform/federation/graphql/dto/MoneyDto.java",
    }
    metrics["floating_commercial_money_hits"] = money_hits
    metrics["floating_commercial_money_paths"] = len(money_paths)
    if not required_unsafe_money_paths.issubset(money_paths):
        errors.append(
            "money scan no longer reproduces classified unsafe paths: "
            f"{sorted(required_unsafe_money_paths - money_paths)}"
        )

    canonical_money_paths = [
        REPO / "billing-module/src/main/java/com/example/platform/billing/domain/BillingDecision.java",
        REPO / "billing-module/src/main/java/com/example/platform/billing/domain/PricingRule.java",
        REPO / "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingInvoiceRepository.java",
        REPO / "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingLedgerJdbcRepository.java",
        REPO / "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentAttemptRepository.java",
        REPO / "commerce-module/src/main/java/com/example/platform/commerce/domain/CanonicalProduct.java",
    ]
    canonical_money_float = re.compile(
        r"\b(?:double|Double|float|Float)\s+\w*(?:amount|price|cost|credit|balance)\w*",
        re.IGNORECASE,
    )
    canonical_money_float_hits = 0
    for path in canonical_money_paths:
        canonical_money_float_hits += len(
            canonical_money_float.findall(path.read_text(encoding="utf-8", errors="replace"))
        )
    metrics["CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT"] = canonical_money_float_hits
    if canonical_money_float_hits != 0:
        errors.append(
            "CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT="
            f"{canonical_money_float_hits} expected=0"
        )

    return errors, metrics


def self_test(contract_text: str, inventory_data: dict) -> list[str]:
    failures: list[str] = []
    controls: list[tuple[str, bool]] = []

    controls.append((
        "remove-separation-token",
        bool(validate_contract(contract_text.replace("ObservedRuntimeUsage != BillableUsage", ""))),
    ))
    controls.append((
        "remove-contract-clause",
        bool(validate_contract(contract_text.replace("### C27 —", "### Z27 —"))),
    ))

    duplicate = copy.deepcopy(inventory_data)
    duplicate["inventory"][1]["id"] = duplicate["inventory"][0]["id"]
    controls.append(("duplicate-inventory-id", bool(validate_inventory(duplicate, check_evidence=False, check_drift=False)[0])))

    invalid_enum = copy.deepcopy(inventory_data)
    invalid_enum["inventory"][0]["disposition"] = "KEEP_MAYBE"
    controls.append(("invalid-disposition", bool(validate_inventory(invalid_enum, check_evidence=False, check_drift=False)[0])))

    unclassified = copy.deepcopy(inventory_data)
    unclassified["inventory"][0]["disposition"] = "UNCLASSIFIED"
    controls.append(("unclassified-entry", bool(validate_inventory(unclassified, check_evidence=False, check_drift=False)[0])))

    no_target = copy.deepcopy(inventory_data)
    no_target["writer_candidate_sets"][0]["candidates"][0]["target_canonical_writer"] = False
    controls.append(("zero-canonical-writer", bool(validate_inventory(no_target, check_evidence=False, check_drift=False)[0])))

    for name, rejected in controls:
        print(f"NEGATIVE_CONTROL {name}: {'PASS' if rejected else 'FAIL'}")
        if not rejected:
            failures.append(name)
    print(f"NEGATIVE_CONTROLS={len(controls) - len(failures)}/{len(controls)} PASS")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true", help="run in-memory mutation controls")
    args = parser.parse_args()

    missing = [path for path in [CONTRACT, INVENTORY] if not path.is_file()]
    if missing:
        print(f"FATAL missing artifacts: {[str(path) for path in missing]}")
        return 2

    contract_text = CONTRACT.read_text(encoding="utf-8")
    try:
        inventory_data = json.loads(INVENTORY.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        print(f"FATAL invalid inventory JSON: {error}")
        return 2

    contract_errors = validate_contract(contract_text)
    inventory_errors, inventory_metrics = validate_inventory(inventory_data)
    repository_errors, repository_metrics = repository_checks()
    errors = contract_errors + inventory_errors + repository_errors

    print(f"CONTRACT_CLAUSES=27/27 {'PASS' if not contract_errors else 'FAIL'}")
    print(
        "INVENTORY="
        f"{inventory_metrics.get('inventory_entry_count', 0)} "
        f"UNCLASSIFIED={inventory_metrics.get('unclassified_count', -1)} "
        f"DUPLICATE_IDS={inventory_metrics.get('duplicate_inventory_id_count', -1)} "
        f"{'PASS' if not inventory_errors else 'FAIL'}"
    )
    print("WRITER_CANDIDATE_CATEGORIES=5 CANONICAL_TARGETS=5")
    for key in sorted(repository_metrics):
        print(f"SCAN {key}={repository_metrics[key]}")

    if args.self_test:
        negative_failures = self_test(contract_text, inventory_data)
        if negative_failures:
            errors.extend(f"negative control failed: {name}" for name in negative_failures)

    if errors:
        print(f"COMMERCIAL_AUTHORITY_GUARD=FAIL errors={len(errors)}")
        for error in errors:
            print(f"FAIL: {error}")
        return 1

    print("ARCHITECTURE_DRIFT_SCAN=PASS")
    print("BILLING_ENTITLEMENT_PAYMENT_DECISION_RECOVERY=PASS")
    print("COMMERCIAL_AUTHORITY_GUARD=PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
