#!/usr/bin/env python3
"""Fail-closed implementation guard for H5 commercial-authority convergence.

The guard is network-free, accepts both dirty pre-commit worktrees and append-forward
committed candidates, and never assumes that retired source paths still exist.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable


REPO = Path(__file__).resolve().parents[4]
GOV = REPO / "docs" / "architecture" / "governance"
CONTRACT = GOV / "billing-entitlement-payment-authority-convergence-bounded-architecture-contract-v1.md"
INVENTORY = GOV / "billing-entitlement-payment-authority-convergence-repository-inventory-v1.json"
ACCEPTED_CANDIDATE_SHA = "586be5a08e90482ddcda9530fb66bd7783637361"
ACCEPTED_CANDIDATE_TREE = "18e093b75887d3e975550489c8e411b8dfdd6690"
FRONTEND_MONEY_PATH = (
    "federation-query-module/src/main/java/com/example/platform/federation/graphql/dto/MoneyDto.java"
)
FRONTEND_CLASSIFICATION = "NON_AUTHORITATIVE_FRONTEND_PROJECTION_OUT_OF_SCOPE"
SOURCE_ROOTS = (
    "shared-kernel", "billing-module", "entitlement-module", "payment-module",
    "commerce-module", "render-module", "platform-app", "worker-fabric-module",
    "media-execution-plan-module", "federation-query-module",
)

REQUIRED_CONTRACT_TOKENS = {
    "Payment != Billing != Subscription != Entitlement != Quota != Usage != Cost != Price",
    "ObservedRuntimeUsage != BillableUsage",
    "Quota != Capacity",
    "Quota != Reservation",
    "Price != ExecutionCost",
    "SubscriptionPlan != CapabilityContract",
    "Entitlement != RuntimeAvailability",
    "PaymentStatus != EntitlementAuthority",
    "EFFECTIVE_CAPABILITY_VIEW_IS_DERIVED_APPLICATION_PROJECTION_V1",
    "ENTITLEMENT_AND_QUOTA_REMAIN_SEPARATE_AUTHORITIES_V1",
    "EXECUTION_COST_IS_NOT_COMMERCIAL_PRICE_AUTHORITY_V1",
    "CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT=0",
    FRONTEND_CLASSIFICATION,
}

REQUIRED_PRODUCTION_PATHS = {
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/Money.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/PrincipalRef.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/CommercialDecision.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/EntitlementDecision.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/QuotaDecision.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/ExecutionCostProjection.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/CommercialAdmissionPort.java",
    "shared-kernel/src/main/java/com/example/platform/shared/commercial/QuotaConsumptionPort.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/app/EntitlementService.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/app/QuotaUsageAuthority.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/app/QuotaDecisionService.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/app/CommercialAdmissionService.java",
    "billing-module/src/main/java/com/example/platform/usage/infrastructure/ObservedRuntimeUsageJdbcRepository.java",
    "billing-module/src/main/java/com/example/platform/billing/usage/BillableUsage.java",
    "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillableUsageJdbcRepository.java",
    "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingInvoiceRepository.java",
    "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingLedgerJdbcRepository.java",
    "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java",
    "commerce-module/src/main/java/com/example/platform/commerce/infrastructure/ProductCatalogJdbcRepository.java",
    "platform-app/src/main/java/com/example/platform/capability/effective/EffectiveCapabilityView.java",
    "platform-app/src/main/java/com/example/platform/capability/effective/EffectiveCapabilityInputs.java",
}

RETIRED_JAVA_SHADOW_PATHS = {
    "render-module/src/main/java/com/example/platform/render/app/QuotaUsageRepository.java",
    "render-module/src/main/java/com/example/platform/render/app/RenderQuotaService.java",
    "quota-billing-module/src/main/java/com/example/platform/quota/app/QuotaService.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/queue/UsageRecordRepository.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecisionEngine.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecision.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecisionRequest.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecisionTraceNode.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/policy/PricingEngine.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/policy/CreditSystem.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/BillingEnforcementService.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/billing/RenderBillingRecordRepository.java",
    "billing-module/src/main/java/com/example/platform/billing/infrastructure/SubscriptionContractRepository.java",
    "payment-module/src/main/java/com/example/platform/payment/app/CheckoutPaymentBindingRegistry.java",
    "billing-module/src/main/java/com/example/platform/billing/domain/PaymentLedgerEntry.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/domain/ExportCapabilityPolicy.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/domain/ProviderAccessPolicy.java",
    "entitlement-module/src/main/java/com/example/platform/entitlement/app/EntitlementPort.java",
}

TABLE_WRITERS = {
    "quota_usage": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/QuotaUsageJdbcRepository.java",
    "quota_usage_operation": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/QuotaUsageJdbcRepository.java",
    "subscription_contract": "billing-module/src/main/java/com/example/platform/billing/infrastructure/SubscriptionJdbcRepository.java",
    "subscription_command": "billing-module/src/main/java/com/example/platform/billing/infrastructure/SubscriptionJdbcRepository.java",
    "subscription_plan": "billing-module/src/main/java/com/example/platform/billing/infrastructure/SubscriptionJdbcRepository.java",
    "entitlement_grant": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/EntitlementGrantRepository.java",
    "entitlement_command_audit": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/EntitlementCommandAuditRepository.java",
    "entitlement_override": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/EntitlementOverrideRepository.java",
    "entitlement_bundle": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/EntitlementBundleRepository.java",
    "quota_profile": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/QuotaProfileRepository.java",
    "workspace_member_entitlement_grant": "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/WorkspaceMemberEntitlementGrantRepository.java",
    "observed_runtime_usage": "billing-module/src/main/java/com/example/platform/usage/infrastructure/ObservedRuntimeUsageJdbcRepository.java",
    "billable_usage": "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillableUsageJdbcRepository.java",
    "pricing_rule": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CommercialPricingJdbcRepository.java",
    "custom_pricing_rule": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CommercialPricingJdbcRepository.java",
    "discount_policy": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CommercialPricingJdbcRepository.java",
    "rated_usage_record": "billing-module/src/main/java/com/example/platform/billing/infrastructure/RatedUsageJdbcRepository.java",
    "billing_invoice": "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingInvoiceRepository.java",
    "billing_invoice_command": "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingInvoiceRepository.java",
    "invoice_line_item": "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingInvoiceRepository.java",
    "billing_ledger_entry": "billing-module/src/main/java/com/example/platform/billing/infrastructure/BillingLedgerJdbcRepository.java",
    "credit_wallet": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CreditWalletJdbcRepository.java",
    "credit_transaction": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CreditWalletJdbcRepository.java",
    "credit_reservation": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CreditWalletJdbcRepository.java",
    "credit_wallet_command": "billing-module/src/main/java/com/example/platform/billing/infrastructure/CreditWalletJdbcRepository.java",
    "payment_transaction": "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java",
    "payment_command": "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java",
    "provider_webhook_receipt": "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java",
    "payment_refund": "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java",
    "payment_outbox": "payment-module/src/main/java/com/example/platform/payment/infrastructure/PaymentTransactionJdbcRepository.java",
    "commerce_product": "commerce-module/src/main/java/com/example/platform/commerce/infrastructure/ProductCatalogJdbcRepository.java",
    "commercial_offering": "commerce-module/src/main/java/com/example/platform/commerce/infrastructure/ProductCatalogJdbcRepository.java",
    "provider_product_mapping": "commerce-module/src/main/java/com/example/platform/commerce/infrastructure/ProductCatalogJdbcRepository.java",
    "product_catalog_command": "commerce-module/src/main/java/com/example/platform/commerce/infrastructure/ProductCatalogJdbcRepository.java",
}


def git(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args], cwd=REPO, text=True, capture_output=True, check=False
    )


def load_production_sources(repo: Path) -> dict[str, str]:
    files: dict[str, str] = {}
    for root_name in SOURCE_ROOTS:
        root = repo / root_name / "src" / "main"
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*.java")):
            relative = path.relative_to(repo).as_posix()
            if "/build/" not in relative:
                files[relative] = path.read_text(encoding="utf-8", errors="replace")
    return files


def validate_accepted_ancestor(object_exists: bool, is_ancestor: bool) -> list[str]:
    if not object_exists:
        return ["ACCEPTED_ANCESTOR_MISSING"]
    if not is_ancestor:
        return ["ACCEPTED_ANCESTOR_NOT_IN_HISTORY"]
    return []


def _writer_paths(files: dict[str, str], table: str) -> list[str]:
    token = re.escape(table)
    patterns = (
        re.compile(rf"\b(?:insertInto|update|deleteFrom|mergeInto)\s*\(\s*{token}\b", re.I),
        re.compile(rf"\b(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM|MERGE\s+INTO)\s+{token}\b", re.I),
    )
    upper_token = table.upper()
    return sorted(path for path, source in files.items()
                  if upper_token in source.upper()
                  and any(pattern.search(source) for pattern in patterns))


def _strip_java_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return re.sub(r"//[^\n]*", "", source)


def validate_implementation(files: dict[str, str]) -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    metrics: dict[str, int] = {}

    missing = sorted(REQUIRED_PRODUCTION_PATHS - set(files))
    metrics["REQUIRED_CANONICAL_PATH_MISSING_COUNT"] = len(missing)
    errors.extend(f"REQUIRED_CANONICAL_PATH_MISSING:{path}" for path in missing)

    retired_present = sorted(RETIRED_JAVA_SHADOW_PATHS & set(files))
    render_shadow_symbols = sum(
        len(re.findall(r"\b(?:BillingDecisionEngine|RenderQuotaService|PricingEngine|CreditSystem|BillingEnforcementService|RenderBillingRecordRepository)\b", source))
        for path, source in files.items() if path.startswith("render-module/src/main/")
    )
    metrics["RENDER_COMMERCIAL_SHADOW_COUNT"] = len(retired_present) + render_shadow_symbols
    if metrics["RENDER_COMMERCIAL_SHADOW_COUNT"]:
        errors.append(
            "RENDER_COMMERCIAL_SHADOW_COUNT="
            f"{metrics['RENDER_COMMERCIAL_SHADOW_COUNT']} paths={retired_present}"
        )

    writer_errors = 0
    for table, expected_path in sorted(TABLE_WRITERS.items()):
        actual = _writer_paths(files, table)
        metrics[f"WRITER_{table}"] = len(actual)
        if actual != [expected_path]:
            writer_errors += 1
            errors.append(
                f"WRITER_{table}=FAIL expected={[expected_path]} actual={actual}"
            )
    metrics["CANONICAL_TABLE_WRITER_ERROR_COUNT"] = writer_errors

    money_pattern = re.compile(
        r"\b(?:double|Double|float|Float)\s+\w*(?:amount|price|credit|balance|charge|invoice|ledger)\w*",
        re.I,
    )
    money_hits = 0
    for path, source in files.items():
        if not path.startswith(("shared-kernel/", "billing-module/", "payment-module/", "commerce-module/")):
            continue
        if (path == FRONTEND_MONEY_PATH or path.endswith("ExecutionCostProjection.java")
                or "Cost" in Path(path).name):
            continue
        money_hits += len(money_pattern.findall(source))
    metrics["CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT"] = money_hits
    if money_hits:
        errors.append(f"CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT={money_hits} expected=0")

    plan_branch = re.compile(
        r"(?:case\s+\"(?:FREE|PRO|TEAM|ENTERPRISE|EXPERIMENTAL)\"|"
        r"\"(?:FREE|PRO|TEAM|ENTERPRISE|EXPERIMENTAL)\"\s*\.equals\s*\()",
        re.I,
    )
    capability_term = re.compile(r"\b(?:capability|provider|runtime|gpu|preset|effect|feature)\b", re.I)
    plan_hits = 0
    for path, source in files.items():
        if not path.startswith(("entitlement-module/src/main/", "commerce-module/src/main/")):
            continue
        stripped = _strip_java_comments(source)
        if plan_branch.search(stripped) and capability_term.search(stripped):
            plan_hits += 1
    metrics["PLAN_SPECIFIC_CAPABILITY_BRANCH_COUNT"] = plan_hits
    if plan_hits:
        errors.append(f"PLAN_SPECIFIC_CAPABILITY_BRANCH_COUNT={plan_hits} expected=0")

    effective_sources = {
        path: source for path, source in files.items()
        if path.startswith("platform-app/src/main/java/com/example/platform/capability/effective/")
    }
    effective_text = "\n".join(effective_sources.values())
    persistence_pattern = re.compile(
        r"\b(?:JdbcTemplate|DSLContext|Repository|EntityManager|@Transactional|insertInto|update\s*\(|deleteFrom|ConcurrentHashMap)\b"
    )
    effective_persistence = len(persistence_pattern.findall(effective_text))
    metrics["EFFECTIVE_CAPABILITY_PERSISTENCE_HITS"] = effective_persistence
    if effective_persistence:
        errors.append(f"EFFECTIVE_CAPABILITY_PERSISTENCE_HITS={effective_persistence} expected=0")
    required_inputs = {
        "CAPABILITY_LIFECYCLE", "H1_RUNTIME_AVAILABILITY", "H5_ENTITLEMENT",
        "H5_COMMERCIAL_QUOTA", "ROLE_WORKSPACE_POLICY",
    }
    missing_inputs = sorted(token for token in required_inputs if token not in effective_text)
    metrics["EFFECTIVE_CAPABILITY_DISTINCT_INPUT_COUNT"] = 5 - len(missing_inputs)
    if missing_inputs:
        errors.append(f"EFFECTIVE_CAPABILITY_INPUTS_MISSING={missing_inputs}")
    h1_internal_effective = len(re.findall(
        r"RuntimeEligibilityEvaluator|RuntimeEligibilityDecision|ProviderCompatibilityGraph|CompatibilityKernel|workerfabric",
        effective_text,
    ))
    metrics["EFFECTIVE_CAPABILITY_H1_INTERNAL_IMPORT_HITS"] = h1_internal_effective
    if h1_internal_effective:
        errors.append(f"EFFECTIVE_CAPABILITY_H1_INTERNAL_IMPORT_HITS={h1_internal_effective} expected=0")

    observed_mutations = 0
    observed_pattern = re.compile(
        r"\b(?:UPDATE|DELETE\s+FROM|\.update\s*\(|deleteFrom\s*\()\s*\(?\s*OBSERVED_RUNTIME_USAGE\b",
        re.I,
    )
    for source in files.values():
        observed_mutations += len(observed_pattern.findall(source))
    metrics["OBSERVED_RUNTIME_USAGE_MUTATION_WRITER_COUNT"] = observed_mutations
    if observed_mutations:
        errors.append(f"OBSERVED_RUNTIME_USAGE_MUTATION_WRITER_COUNT={observed_mutations} expected=0")
    billable = files.get(
        "billing-module/src/main/java/com/example/platform/billing/usage/BillableUsage.java", ""
    )
    required_billable = {
        "observedUsageId", "meteringRuleId", "meteringRuleVersion",
        "transformationKind", "sourceObservationTimestamp", "provenanceReference",
    }
    missing_billable = sorted(token for token in required_billable if token not in billable)
    metrics["BILLABLE_USAGE_PROVENANCE_FIELD_COUNT"] = len(required_billable) - len(missing_billable)
    if missing_billable:
        errors.append(f"BILLABLE_USAGE_PROVENANCE_FIELDS_MISSING={missing_billable}")

    raw_payload_pattern = re.compile(
        r"\b(?:raw_payload|rawPayloadColumn|payload_json|WEBHOOK_BODY|setRawPayload)\b",
        re.I,
    )
    raw_payload_hits = sum(
        len(raw_payload_pattern.findall(source)) for path, source in files.items()
        if path.startswith("payment-module/src/main/")
    )
    metrics["RAW_PAYMENT_WEBHOOK_PAYLOAD_PERSISTENCE_HITS"] = raw_payload_hits
    if raw_payload_hits:
        errors.append(f"RAW_PAYMENT_WEBHOOK_PAYLOAD_PERSISTENCE_HITS={raw_payload_hits} expected=0")

    payment_entitlement_imports = sum(
        len(re.findall(r"^import\s+com\.example\.platform\.(?:entitlement|subscription)\.", source, re.M))
        for path, source in files.items() if path.startswith("payment-module/src/main/")
    )
    metrics["PAYMENT_TO_ENTITLEMENT_AUTHORITY_IMPORT_HITS"] = payment_entitlement_imports
    if payment_entitlement_imports:
        errors.append(f"PAYMENT_TO_ENTITLEMENT_AUTHORITY_IMPORT_HITS={payment_entitlement_imports} expected=0")

    h1_import_pattern = re.compile(
        r"^import\s+com\.example\.platform\.(?:workerfabric|execution\.compatibility|render\.infrastructure\.(?:provider|scheduler|capacity|reservation))\.",
        re.M,
    )
    h5_h1_imports = sum(
        len(h1_import_pattern.findall(source)) for path, source in files.items()
        if path.startswith(("billing-module/src/main/", "entitlement-module/src/main/",
                            "payment-module/src/main/", "commerce-module/src/main/"))
    )
    metrics["H5_TO_H1_INTERNAL_IMPORT_HITS"] = h5_h1_imports
    if h5_h1_imports:
        errors.append(f"H5_TO_H1_INTERNAL_IMPORT_HITS={h5_h1_imports} expected=0")

    commercial_import_pattern = re.compile(
        r"^import\s+com\.example\.platform\.(?:billing|entitlement|payment|commerce|quota)\.",
        re.M,
    )
    h1_h5_imports = sum(
        len(commercial_import_pattern.findall(source)) for path, source in files.items()
        if path.startswith(("worker-fabric-module/src/main/", "media-execution-plan-module/src/main/",
                            "render-module/src/main/"))
    )
    metrics["H1_TO_H5_COMMERCIAL_AUTHORITY_IMPORT_HITS"] = h1_h5_imports
    if h1_h5_imports:
        errors.append(f"H1_TO_H5_COMMERCIAL_AUTHORITY_IMPORT_HITS={h1_h5_imports} expected=0")

    frontend_present = int(FRONTEND_MONEY_PATH in files)
    metrics["FRONTEND_MONEY_PROJECTION_EXPLICIT_EXCLUSION_COUNT"] = frontend_present
    if not frontend_present:
        errors.append("FRONTEND_MONEY_PROJECTION_EXPLICIT_EXCLUSION_COUNT=0 expected=1")

    billing_reconciliation = files.get(
        "billing-module/src/main/java/com/example/platform/billing/app/ReconciliationService.java", ""
    )
    payment_shadow_hits = sum(
        billing_reconciliation.count(token)
        for token in ("paymentLedger", "addPaymentEntry", "PaymentLedgerEntry")
    )
    metrics["BILLING_PAYMENT_LEDGER_SHADOW_HITS"] = payment_shadow_hits
    if payment_shadow_hits:
        errors.append(f"BILLING_PAYMENT_LEDGER_SHADOW_HITS={payment_shadow_hits} expected=0")

    return sorted(set(errors)), metrics


def validate_contract(text: str) -> list[str]:
    errors = [f"CONTRACT_MISSING_TOKEN:{token}" for token in sorted(REQUIRED_CONTRACT_TOKENS)
              if token not in text]
    clauses = [int(value) for value in re.findall(r"^### C(\d+) —", text, re.M)]
    if clauses != list(range(1, 28)):
        errors.append(f"CONTRACT_CLAUSE_ORDER={clauses} expected=1..27")
    phases = [int(value) for value in re.findall(r"^\| I(\d+) \|", text, re.M)]
    if phases != list(range(11)):
        errors.append(f"CONTRACT_PHASE_ORDER={phases} expected=0..10")
    return errors


def _delete_shadow_retired(entry_id: str, path: str, files: dict[str, str]) -> bool:
    if entry_id == "INV-010":
        settings = (REPO / "settings.gradle.kts").read_text(encoding="utf-8")
        platform_build = (REPO / "platform-app/build.gradle.kts").read_text(encoding="utf-8")
        return "quota-billing-module" not in settings + platform_build and not (REPO / "quota-billing-module").exists()
    if entry_id == "INV-036":
        source = files.get(path, "")
        return all(token not in source for token in ("paymentLedger", "addPaymentEntry", "PaymentLedgerEntry"))
    return path not in files and not (REPO / path).is_file()


def validate_inventory(data: dict, files: dict[str, str]) -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    metrics: dict[str, int] = {}
    entries = data.get("inventory")
    if not isinstance(entries, list):
        return ["INVENTORY_NOT_A_LIST"], metrics
    ids = [entry.get("id") for entry in entries]
    metrics["INVENTORY_ENTRY_COUNT"] = len(entries)
    metrics["INVENTORY_DUPLICATE_ID_COUNT"] = len(ids) - len(set(ids))
    metrics["INVENTORY_UNCLASSIFIED_COUNT"] = sum(
        entry.get("disposition") == "UNCLASSIFIED" for entry in entries
    )
    if metrics["INVENTORY_DUPLICATE_ID_COUNT"]:
        errors.append("INVENTORY_DUPLICATE_IDS")
    if metrics["INVENTORY_UNCLASSIFIED_COUNT"]:
        errors.append("INVENTORY_UNCLASSIFIED")

    frontend = [entry for entry in entries if entry.get("path") == FRONTEND_MONEY_PATH]
    frontend_ok = len(frontend) == 1 and frontend[0].get("disposition") == FRONTEND_CLASSIFICATION
    metrics["FRONTEND_PROJECTION_RECLASSIFIED_COUNT"] = int(frontend_ok)
    if not frontend_ok:
        errors.append("FRONTEND_PROJECTION_RECLASSIFICATION_MISSING")

    delete_entries = [entry for entry in entries if entry.get("disposition") == "DELETE_SHADOW"]
    retired = [entry for entry in delete_entries
               if _delete_shadow_retired(str(entry.get("id")), str(entry.get("path")), files)]
    metrics["ACCEPTED_DELETE_SHADOW_COUNT"] = len(delete_entries)
    metrics["ACCEPTED_DELETE_SHADOW_RETIRED_COUNT"] = len(retired)
    if len(retired) != len(delete_entries):
        remaining = sorted(str(entry.get("id")) for entry in delete_entries if entry not in retired)
        errors.append(f"ACCEPTED_DELETE_SHADOWS_REMAIN={remaining}")

    declared = data.get("summary", {}).get("disposition_counts", {})
    computed = Counter(str(entry.get("disposition")) for entry in entries)
    all_dispositions = set(declared) | set(computed)
    normalized_declared = {key: int(declared.get(key, 0)) for key in all_dispositions}
    normalized_computed = {key: int(computed.get(key, 0)) for key in all_dispositions}
    if normalized_declared != normalized_computed:
        errors.append(f"INVENTORY_DISPOSITION_SUMMARY_DRIFT declared={declared} computed={dict(computed)}")
    return errors, metrics


def repository_history_errors() -> tuple[list[str], dict[str, int | str]]:
    metrics: dict[str, int | str] = {}
    exists = git("cat-file", "-e", f"{ACCEPTED_CANDIDATE_SHA}^{{commit}}").returncode == 0
    ancestor = exists and git("merge-base", "--is-ancestor", ACCEPTED_CANDIDATE_SHA, "HEAD").returncode == 0
    errors = validate_accepted_ancestor(exists, ancestor)
    metrics["ACCEPTED_ANCESTOR_OBJECT_PRESENT"] = int(exists)
    metrics["ACCEPTED_ANCESTOR_IN_HEAD_HISTORY"] = int(ancestor)
    if exists:
        tree = git("rev-parse", f"{ACCEPTED_CANDIDATE_SHA}^{{tree}}").stdout.strip()
        metrics["ACCEPTED_ANCESTOR_TREE_MATCH"] = int(tree == ACCEPTED_CANDIDATE_TREE)
        if tree != ACCEPTED_CANDIDATE_TREE:
            errors.append(f"ACCEPTED_ANCESTOR_TREE_MISMATCH actual={tree}")
    else:
        metrics["ACCEPTED_ANCESTOR_TREE_MATCH"] = 0
    metrics["HEAD"] = git("rev-parse", "HEAD").stdout.strip()
    changed = set(git("diff", "--name-only").stdout.splitlines())
    changed.update(git("diff", "--cached", "--name-only").stdout.splitlines())
    changed.update(git("ls-files", "--others", "--exclude-standard").stdout.splitlines())
    metrics["WORKTREE_CHANGED_PATH_COUNT"] = len(changed)
    return errors, metrics


def mutation_self_test(files: dict[str, str]) -> list[str]:
    controls: list[tuple[str, dict[str, str], str]] = []

    def mutated(path: str, source: str) -> dict[str, str]:
        result = dict(files)
        result[path] = source
        return result

    controls.append(("added_quota_writer", mutated(
        "render-module/src/main/java/example/InjectedQuotaWriter.java",
        "class X { void x(){ dsl.insertInto(QUOTA_USAGE); } }"), "WRITER_quota_usage"))
    controls.append(("canonical_money_float", mutated(
        "billing-module/src/main/java/com/example/platform/billing/domain/InjectedMoney.java",
        "record X(double priceAmount) {}"), "CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT"))
    controls.append(("render_commercial_shadow", mutated(
        "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecisionEngine.java",
        "class BillingDecisionEngine {}"), "RENDER_COMMERCIAL_SHADOW_COUNT"))
    controls.append(("plan_capability_fork", mutated(
        "entitlement-module/src/main/java/com/example/platform/entitlement/app/InjectedCapabilityPolicy.java",
        'class X { boolean capability(String tier){ return "PRO".equals(tier); } }'),
        "PLAN_SPECIFIC_CAPABILITY_BRANCH_COUNT"))
    controls.append(("effective_capability_persistence", mutated(
        "platform-app/src/main/java/com/example/platform/capability/effective/InjectedStore.java",
        "class X { JdbcTemplate jdbcTemplate; }"), "EFFECTIVE_CAPABILITY_PERSISTENCE_HITS"))
    controls.append(("raw_webhook_payload", mutated(
        "payment-module/src/main/java/com/example/platform/payment/infrastructure/InjectedWebhookWriter.java",
        'class X { String rawPayload; String sql="INSERT INTO provider_webhook_receipt(raw_payload)"; }'),
        "RAW_PAYMENT_WEBHOOK_PAYLOAD_PERSISTENCE_HITS"))
    controls.append(("observed_usage_mutation", mutated(
        "billing-module/src/main/java/com/example/platform/usage/infrastructure/InjectedObservationMutation.java",
        'class X { String sql="UPDATE observed_runtime_usage SET quantity=0"; }'),
        "OBSERVED_RUNTIME_USAGE_MUTATION_WRITER_COUNT"))

    failures: list[str] = []
    for name, candidate, code in controls:
        errors, _ = validate_implementation(candidate)
        rejected = any(code in error for error in errors)
        print(f"MUTATION_{name.upper()}={'PASS' if rejected else 'FAIL'}")
        if not rejected:
            failures.append(name)

    history_controls = {
        "missing_accepted_ancestor": validate_accepted_ancestor(False, False),
        "stale_accepted_ancestor": validate_accepted_ancestor(True, False),
    }
    for name, errors in history_controls.items():
        rejected = bool(errors)
        print(f"MUTATION_{name.upper()}={'PASS' if rejected else 'FAIL'}")
        if not rejected:
            failures.append(name)
    print(f"MUTATION_SELF_TESTS={len(controls) + len(history_controls) - len(failures)}/{len(controls) + len(history_controls)}")
    return failures


def _print_metrics(metrics: dict[str, int | str]) -> None:
    for key in sorted(metrics):
        print(f"{key}={metrics[key]}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    missing_artifacts = [path for path in (CONTRACT, INVENTORY) if not path.is_file()]
    if missing_artifacts:
        print("COMMERCIAL_AUTHORITY_GUARD=FAIL")
        for path in missing_artifacts:
            print(f"ERROR=REQUIRED_ARTIFACT_MISSING:{path.relative_to(REPO)}")
        return 2

    try:
        inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print("COMMERCIAL_AUTHORITY_GUARD=FAIL")
        print(f"ERROR=INVENTORY_INVALID:{error}")
        return 2

    files = load_production_sources(REPO)
    contract_errors = validate_contract(CONTRACT.read_text(encoding="utf-8"))
    inventory_errors, inventory_metrics = validate_inventory(inventory, files)
    implementation_errors, implementation_metrics = validate_implementation(files)
    history_errors, history_metrics = repository_history_errors()
    errors = contract_errors + inventory_errors + implementation_errors + history_errors

    _print_metrics(inventory_metrics)
    _print_metrics(implementation_metrics)
    _print_metrics(history_metrics)

    if args.self_test:
        failures = mutation_self_test(files)
        errors.extend(f"MUTATION_SELF_TEST_FAILED:{name}" for name in failures)

    if errors:
        print("COMMERCIAL_AUTHORITY_GUARD=FAIL")
        for error in sorted(set(errors)):
            print(f"ERROR={error}")
        return 1

    print("ARCHITECTURE_DRIFT_SCAN=PASS")
    print("COMMERCIAL_AUTHORITY_GUARD=PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
