#!/usr/bin/env python3
"""Mutation tests proving the Phase20 contract guard fails closed."""
from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/phase20-resource-accounting-contract-guard.py"
CONTRACT = ROOT / "docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-bounded-architecture-contract-v1.md"
LEDGER = ROOT / "docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-disposition-ledger-v1.json"
INVENTORY = ROOT / "docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-repository-reality-inventory-v1.json"


def run(contract: pathlib.Path, ledger: pathlib.Path, inventory: pathlib.Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(GUARD), "--root", str(ROOT), "--contract", str(contract), "--ledger", str(ledger), "--inventory", str(inventory), "--skip-scope"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def expect_red(
        name: str,
        contract_text: str,
        ledger_data: dict,
        inventory_data: dict,
        temp: pathlib.Path,
        required_error: str | None = None) -> None:
    contract = temp / f"{name}.md"
    ledger = temp / f"{name}-ledger.json"
    inventory = temp / f"{name}-inventory.json"
    contract.write_text(contract_text, encoding="utf-8")
    ledger.write_text(json.dumps(ledger_data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    inventory.write_text(json.dumps(inventory_data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    result = run(contract, ledger, inventory)
    if result.returncode == 0:
        raise AssertionError(f"mutation unexpectedly passed: {name}")
    if required_error is not None and required_error not in result.stderr:
        raise AssertionError(
            f"mutation failed through the wrong predicate: {name}: {result.stderr!r}")
    print(f"MUTATION_{name}=PASS")


def main() -> int:
    original_contract = CONTRACT.read_text(encoding="utf-8")
    original_ledger = json.loads(LEDGER.read_text(encoding="utf-8"))
    original_inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    green = run(CONTRACT, LEDGER, INVENTORY)
    if green.returncode != 0:
        print(green.stdout, end="")
        print(green.stderr, end="", file=sys.stderr)
        raise AssertionError("unmutated contract does not pass")

    with tempfile.TemporaryDirectory(prefix="phase20-contract-mutations-") as directory:
        temp = pathlib.Path(directory)
        cases = []
        cases.append(("01_REMOVE_CLAUSE", original_contract.replace("## C17 — Typed decision and incompatibility explanation", "## Removed C17", 1), original_ledger, original_inventory))
        cases.append(("02_DUPLICATE_CLAUSE", original_contract.replace("## C18 — Semantic digest exclusion", "## C17 — Duplicate\n\n## C18 — Semantic digest exclusion", 1), original_ledger, original_inventory))
        cases.append(("03_COLLAPSE_TAXONOMY", original_contract.replace("ExecutionRequirement != Capacity != Reservation != ObservedUsage != Quota != Cost", "ExecutionRequirement = Capacity = Reservation = ObservedUsage = Quota = Cost", 1), original_ledger, original_inventory))
        cases.append(("04_REMOVE_GLOBAL_VERSION_LAW", original_contract.replace("NO_GLOBAL_NATIVE_TOOL_VERSION_AUTHORITY_V1", "REMOVED_GLOBAL_NATIVE_VERSION_LAW", 1), original_ledger, original_inventory))
        cases.append(("05_PEP_FALSE_RETAIN", original_contract.replace("PHYSICAL_EXECUTION_PLAN_REVIEW_RESULT=PHYSICAL_EXECUTION_PLAN_COLLAPSE_OR_DOWNGRADE_CANDIDATE", "PHYSICAL_EXECUTION_PLAN_REVIEW_RESULT=RETAIN_UNCHANGED", 1), original_ledger, original_inventory))
        cases.append(("06_PREMATURE_IMPLEMENTATION_GO", original_contract.replace("IMPLEMENTATION_AUTHORIZATION=NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE", "IMPLEMENTATION_AUTHORIZATION=GO", 1), original_ledger, original_inventory))

        unclassified = json.loads(json.dumps(original_ledger))
        unclassified["rows"][0]["disposition"] = "UNCLASSIFIED"
        unclassified["unclassified_count"] = 1
        cases.append(("07_UNCLASSIFIED_LEDGER", original_contract, unclassified, original_inventory))

        duplicate = json.loads(json.dumps(original_ledger))
        duplicate["rows"][1]["id"] = duplicate["rows"][0]["id"]
        duplicate["duplicate_id_count"] = 1
        cases.append(("08_DUPLICATE_LEDGER_ID", original_contract, duplicate, original_inventory))

        missing_path = json.loads(json.dumps(original_ledger))
        missing_path["rows"][0]["member_paths"] = ["does/not/exist/Phase20Missing.java"]
        missing_path["missing_member_path_count"] = 0
        cases.append((
            "09_HISTORICAL_BASE_MISSING_MEMBER_PATH",
            original_contract,
            missing_path,
            original_inventory,
            "historical-base ledger member paths missing at",
        ))

        unclassified_inventory = json.loads(json.dumps(original_inventory))
        unclassified_inventory["unclassified_finding_count"] = 1
        cases.append((
            "10_UNCLASSIFIED_INVENTORY",
            original_contract,
            original_ledger,
            unclassified_inventory,
            None,
        ))

        for case in cases:
            if len(case) == 4:
                name, contract_text, ledger_data, inventory_data = case
                required_error = None
            else:
                name, contract_text, ledger_data, inventory_data, required_error = case
            expect_red(
                name, contract_text, ledger_data, inventory_data, temp, required_error)

    print("GUARD_GREEN_BEHAVIOR=PASS")
    print("GUARD_RED_BEHAVIOR=10/10")
    print("PHASE20_RESOURCE_ACCOUNTING_CONTRACT_GUARD_MUTATION_TESTS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
