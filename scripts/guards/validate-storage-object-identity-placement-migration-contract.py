#!/usr/bin/env python3
"""Validate the frozen Storage identity/placement migration decision artifacts."""

from __future__ import annotations

import argparse
import csv
import re
import shutil
import sys
import tempfile
from collections import Counter
from pathlib import Path


CONTRACT_REL = Path(
    "docs/architecture/governance/"
    "storage-object-identity-and-physical-placement-authority-migration-contract-v1.md"
)
LEDGER_REL = Path(
    "docs/architecture/governance/storage-object-identity-placement-migration-v1"
)
CONTRACT_TOKEN = (
    "STORAGE_OBJECT_IDENTITY_AND_PHYSICAL_PLACEMENT_AUTHORITY_MIGRATION_CONTRACT_V1"
)

SCHEMAS = {
    "writer-inventory.tsv": [
        "writer_id", "revision", "module", "path", "symbol", "classification",
        "input_semantics", "output_sink", "evidence", "status",
    ],
    "reader-inventory.tsv": [
        "reader_id", "revision", "module", "path", "symbol", "classification",
        "input_semantics", "evidence", "status",
    ],
    "owner-matrix.tsv": [
        "owner_id", "semantic", "authority_owner", "persistence_owner",
        "allowed_writers", "non_owner_rule", "permanent_dual_authority",
    ],
    "persisted-row-classification-feasibility.tsv": [
        "class_id", "proof_condition", "migration_action", "fail_behavior",
        "idempotency", "row_count",
    ],
    "schema-feasibility.tsv": [
        "decision_id", "current_surface", "evidence", "gap", "target_authority",
        "decision", "required_change", "generic_json_allowed",
    ],
    "migration-state-machine.tsv": [
        "state_id", "order", "entry_condition", "required_action", "exit_gate",
        "rollback_policy",
    ],
    "temporary-compatibility-removal-ledger.tsv": [
        "compatibility_id", "type", "entry_condition", "owner", "scope",
        "removal_criterion", "zero_count_gate", "permanent_allowed",
    ],
    "future-guard-red-control-plan.tsv": [
        "guard_id", "prohibited_pattern", "enforcement_scope", "implementation_gate",
        "future_red_plan", "owner", "status",
    ],
}

WRITER_COUNTS = {
    "LEGACY_PHYSICAL_VALUE_WRITER": ("PHYSICAL_TO_LOGICAL_ID_WRITER_COUNT", 3),
    "CANONICAL_LOGICAL_ID_WRITER": ("CANONICAL_LOGICAL_ID_WRITER_COUNT", 1),
    "AMBIGUOUS_WRITER": ("AMBIGUOUS_WRITER_COUNT", 1),
}
READER_COUNTS = {
    "LOGICAL_ID_TO_PHYSICAL_READER": ("LOGICAL_ID_TO_PHYSICAL_READER_COUNT", 4),
    "CANONICAL_LOGICAL_ID_CONSUMER": ("CANONICAL_LOGICAL_ID_CONSUMER_COUNT", 2),
    "AMBIGUOUS_READER": ("AMBIGUOUS_READER_COUNT", 2),
    "STORAGE_OWNER_RESOLVER": ("STORAGE_OWNER_RESOLVER_COUNT", 3),
}
PERSISTED_CLASSES = {
    "CANONICAL_LOGICAL",
    "LEGACY_PHYSICAL_ENCODED",
    "AMBIGUOUS",
}
FUTURE_GUARDS = {
    "BAN_PHYSICAL_VALUE_STORAGE_OBJECT_ID_CONSTRUCTION_OUTSIDE_STORAGE",
    "BAN_STORAGE_OBJECT_ID_URI_PARSING_OUTSIDE_MIGRATION_BOUNDARY",
    "BAN_ARTIFACT_DIRECT_PHYSICAL_PLACEMENT_AUTHORITY",
    "BAN_WEB_DIRECT_PHYSICAL_PLACEMENT_AUTHORITY",
    "BAN_PERMANENT_DUAL_WRITE",
    "BAN_PERMANENT_COMPATIBILITY_FALLBACK",
    "BAN_RAW_STORAGE_LOCATION_PRODUCT_PROJECTION",
    "BAN_UNCLASSIFIED_STORAGE_IDENTITY_WRITER",
    "BAN_UNCLASSIFIED_STORAGE_IDENTITY_READER",
}
REQUIRED_TOKENS = {
    "CANONICAL_MIGRATION_CONTRACT_COUNT": "1",
    "DUAL_AUTHORITY_CONTRACT_COUNT": "0",
    "STORAGE_OBJECT_ID_MODEL": "STORAGE_OBJECT_ID_IS_LOGICAL_STABLE_IDENTITY_V1",
    "STORAGE_OBJECT_ID_OWNER": "STORAGE",
    "PHYSICAL_PLACEMENT_OWNER": "STORAGE",
    "WRITER_INVENTORY_TOTAL": "5",
    "WRITER_UNCLASSIFIED_COUNT": "0",
    "READER_INVENTORY_TOTAL": "11",
    "READER_UNCLASSIFIED_COUNT": "0",
    "UNCLASSIFIED": "0",
    "CANONICAL_LOGICAL_ROW_COUNT": "UNKNOWN",
    "LEGACY_PHYSICAL_ENCODED_ROW_COUNT": "UNKNOWN",
    "AMBIGUOUS_PERSISTED_ROW_COUNT": "UNKNOWN",
    "PERSISTED_ROW_CLASSIFICATION_FEASIBILITY": "PASS",
    "SCHEMA_FEASIBILITY_DECISION": "SCHEMA_MIGRATION_REQUIRED",
    "GENERIC_JSON_PLACEMENT_ALLOWED": "NO",
    "TEMPORARY_MIGRATION_COMPATIBILITY": "ALLOWED_IF_REQUIRED",
    "PERMANENT_DUAL_WRITE_ALLOWED": "NO",
    "PERMANENT_READ_FALLBACK_ALLOWED": "NO",
    "PERMANENT_DUAL_AUTHORITY_ALLOWED": "NO",
    "NEW_LEGACY_PHYSICAL_ROWS_ALLOWED_AFTER_WRITER_CUTOVER": "NO",
    "MIGRATION_STATE_FIRST": "M0",
    "MIGRATION_STATE_LAST": "M7",
    "MIGRATION_STATE_COUNT": "8",
    "FUTURE_GUARD_COUNT": "9",
    "H6_SOURCE_EDIT_COUNT": "0",
    "H6_FOLLOWUP_ENCAPSULATION_HARDENING": "REQUIRED",
}


class ValidationFailure(Exception):
    pass


def read_tsv(path: Path, expected_header: list[str], errors: list[str]) -> list[dict[str, str]]:
    if not path.is_file():
        errors.append(f"missing ledger: {path}")
        return []
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        if reader.fieldnames != expected_header:
            errors.append(
                f"invalid ledger schema for {path.name}: expected {expected_header}, got {reader.fieldnames}"
            )
            return []
        rows = list(reader)
    if not rows:
        errors.append(f"empty ledger: {path.name}")
    for number, row in enumerate(rows, start=2):
        extras = row.get(None)
        if extras:
            errors.append(f"extra TSV fields in {path.name}:{number}")
        for field in expected_header:
            if row.get(field) is None or not row[field].strip():
                errors.append(f"blank {field} in {path.name}:{number}")
    return rows


def contract_tokens(text: str, errors: list[str]) -> dict[str, str]:
    pairs = re.findall(r"^([A-Z][A-Z0-9_]*)=([^\r\n]+)$", text, flags=re.MULTILINE)
    counts = Counter(key for key, _ in pairs)
    for key, count in counts.items():
        if count != 1:
            errors.append(f"duplicate contract final token: {key}")
    tokens = dict(pairs)
    for key, expected in REQUIRED_TOKENS.items():
        actual = tokens.get(key)
        if actual != expected:
            errors.append(f"contract token {key}: expected {expected}, got {actual}")
    for _, (key, expected) in WRITER_COUNTS.items():
        if tokens.get(key) != str(expected):
            errors.append(f"contract token {key}: expected {expected}, got {tokens.get(key)}")
    for _, (key, expected) in READER_COUNTS.items():
        if tokens.get(key) != str(expected):
            errors.append(f"contract token {key}: expected {expected}, got {tokens.get(key)}")
    return tokens


def duplicate_values(rows: list[dict[str, str]], field: str) -> list[str]:
    values = [row[field] for row in rows if row.get(field)]
    return sorted(value for value, count in Counter(values).items() if count > 1)


def validate(root: Path) -> None:
    errors: list[str] = []
    contract_path = root / CONTRACT_REL
    if not contract_path.is_file():
        raise ValidationFailure(f"missing canonical contract: {contract_path}")
    contract_text = contract_path.read_text(encoding="utf-8")
    tokens = contract_tokens(contract_text, errors)

    governance = root / "docs/architecture/governance"
    token_files = []
    if governance.is_dir():
        for markdown in governance.rglob("*.md"):
            if CONTRACT_TOKEN in markdown.read_text(encoding="utf-8"):
                token_files.append(markdown)
    token_occurrences = sum(
        path.read_text(encoding="utf-8").count(CONTRACT_TOKEN) for path in token_files
    )
    if token_occurrences != 1 or token_files != [contract_path]:
        errors.append(
            "canonical contract token/count violation: "
            f"occurrences={token_occurrences}, files={[str(p.relative_to(root)) for p in token_files]}"
        )

    ledgers: dict[str, list[dict[str, str]]] = {}
    for filename, schema in SCHEMAS.items():
        ledgers[filename] = read_tsv(root / LEDGER_REL / filename, schema, errors)

    all_ids: list[str] = []
    for filename, rows in ledgers.items():
        id_field = SCHEMAS[filename][0]
        all_ids.extend(row[id_field] for row in rows if row.get(id_field))
    duplicated_ids = sorted(value for value, count in Counter(all_ids).items() if count > 1)
    if duplicated_ids:
        errors.append(f"duplicate ledger IDs: {duplicated_ids}")

    writers = ledgers["writer-inventory.tsv"]
    readers = ledgers["reader-inventory.tsv"]
    for label, rows in (("writer", writers), ("reader", readers)):
        duplicates = duplicate_values(rows, "path")
        if duplicates:
            errors.append(f"duplicate {label} inventory paths: {duplicates}")
        unclassified = [row for row in rows if row.get("status") != "CLASSIFIED"]
        if unclassified:
            errors.append(f"{label} inventory contains unclassified rows")

    writer_actual = Counter(row.get("classification") for row in writers)
    if len(writers) != 5:
        errors.append(f"writer inventory total/parity: expected 5, got {len(writers)}")
    for classification, (token, expected) in WRITER_COUNTS.items():
        actual = writer_actual[classification]
        if actual != expected or tokens.get(token) != str(actual):
            errors.append(
                f"writer arithmetic/parity {classification}: ledger={actual}, expected={expected}, token={tokens.get(token)}"
            )
    unknown_writer_classes = set(writer_actual) - set(WRITER_COUNTS)
    if unknown_writer_classes:
        errors.append(f"unclassified writer classifications: {sorted(unknown_writer_classes)}")

    reader_actual = Counter(row.get("classification") for row in readers)
    if len(readers) != 11:
        errors.append(f"reader inventory total/parity: expected 11, got {len(readers)}")
    for classification, (token, expected) in READER_COUNTS.items():
        actual = reader_actual[classification]
        if actual != expected or tokens.get(token) != str(actual):
            errors.append(
                f"reader arithmetic/parity {classification}: ledger={actual}, expected={expected}, token={tokens.get(token)}"
            )
    unknown_reader_classes = set(reader_actual) - set(READER_COUNTS)
    if unknown_reader_classes:
        errors.append(f"unclassified reader classifications: {sorted(unknown_reader_classes)}")

    owners = ledgers["owner-matrix.tsv"]
    required_semantics = {"STORAGE_OBJECT_LOGICAL_IDENTITY", "PHYSICAL_PLACEMENT"}
    semantic_counts = Counter(row.get("semantic") for row in owners)
    if set(semantic_counts) != required_semantics:
        errors.append(f"owner semantics mismatch: {sorted(semantic_counts)}")
    for semantic in required_semantics:
        if semantic_counts[semantic] != 1:
            errors.append(f"duplicate or missing authority for {semantic}: {semantic_counts[semantic]}")
    for row in owners:
        if (
            row.get("authority_owner") != "STORAGE"
            or row.get("persistence_owner") != "STORAGE"
            or row.get("permanent_dual_authority") != "NO"
        ):
            errors.append(f"invalid owner or dual authority flag: {row.get('owner_id')}")

    persisted = ledgers["persisted-row-classification-feasibility.tsv"]
    actual_classes = {row.get("class_id") for row in persisted}
    if actual_classes != PERSISTED_CLASSES or len(persisted) != 3:
        errors.append(f"persisted classification completeness: {sorted(actual_classes)}")
    for row in persisted:
        if row.get("row_count") != "UNKNOWN":
            errors.append(f"persisted row count must remain UNKNOWN: {row.get('class_id')}")
        combined = " ".join(row.values()).lower()
        if "string shape alone" not in combined:
            errors.append(f"persisted proof must reject string shape alone: {row.get('class_id')}")

    schema_rows = ledgers["schema-feasibility.tsv"]
    if len(schema_rows) != 1:
        errors.append(f"schema decision uniqueness: expected 1, got {len(schema_rows)}")
    elif (
        schema_rows[0].get("decision") != "SCHEMA_MIGRATION_REQUIRED"
        or schema_rows[0].get("generic_json_allowed") != "NO"
    ):
        errors.append("invalid schema decision or generic JSON flag")

    states = ledgers["migration-state-machine.tsv"]
    expected_states = [f"M{number}" for number in range(8)]
    actual_states = [row.get("state_id") for row in states]
    actual_orders = [row.get("order") for row in states]
    if actual_states != expected_states or actual_orders != [str(number) for number in range(8)]:
        errors.append(
            f"migration state completeness/order: states={actual_states}, orders={actual_orders}"
        )

    compatibility = ledgers["temporary-compatibility-removal-ledger.tsv"]
    if not compatibility:
        errors.append("temporary compatibility ledger must not be empty")
    for row in compatibility:
        if row.get("owner") != "STORAGE":
            errors.append(f"compatibility owner must be STORAGE: {row.get('compatibility_id')}")
        if row.get("permanent_allowed") != "NO":
            errors.append(f"permanent compatibility/fallback forbidden: {row.get('compatibility_id')}")
        if not row.get("entry_condition") or not row.get("scope") or not row.get("removal_criterion"):
            errors.append(f"incomplete compatibility lifecycle: {row.get('compatibility_id')}")
        if not re.fullmatch(r"[A-Z0-9_]+=0", row.get("zero_count_gate", "")):
            errors.append(f"invalid compatibility zero-count gate: {row.get('compatibility_id')}")

    guards = ledgers["future-guard-red-control-plan.tsv"]
    actual_guards = {row.get("guard_id") for row in guards}
    if actual_guards != FUTURE_GUARDS or len(guards) != 9:
        errors.append(f"future guard completeness: {sorted(actual_guards)}")
    for row in guards:
        if row.get("owner") != "STORAGE" or row.get("status") != "PLANNED_RED":
            errors.append(f"future guard owner/status invalid: {row.get('guard_id')}")
        red_plan = row.get("future_red_plan", "").lower()
        if "must fail" not in red_plan or "fixture" not in red_plan:
            errors.append(f"future guard missing executable RED plan: {row.get('guard_id')}")

    if errors:
        raise ValidationFailure("\n".join(f"- {error}" for error in errors))


def copy_artifacts(source_root: Path, target_root: Path) -> None:
    target_contract = target_root / CONTRACT_REL
    target_contract.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_root / CONTRACT_REL, target_contract)
    shutil.copytree(source_root / LEDGER_REL, target_root / LEDGER_REL)


def rewrite_tsv(path: Path, transform) -> None:
    lines = path.read_text(encoding="utf-8").splitlines()
    path.write_text("\n".join(transform(lines)) + "\n", encoding="utf-8")


def expect_rejection(root: Path, name: str, mutate, expected_fragment: str) -> None:
    with tempfile.TemporaryDirectory(prefix=f"storage-contract-{name}-") as temp:
        mutated_root = Path(temp)
        copy_artifacts(root, mutated_root)
        mutate(mutated_root)
        try:
            validate(mutated_root)
        except ValidationFailure as failure:
            if expected_fragment not in str(failure):
                raise ValidationFailure(
                    f"self-test {name} rejected for the wrong reason; "
                    f"expected {expected_fragment!r}, got:\n{failure}"
                ) from failure
            print(f"SELF_TEST {name}=PASS")
            return
        raise ValidationFailure(f"self-test {name} accepted a forbidden mutation")


def self_test(root: Path) -> None:
    validate(root)

    expect_rejection(
        root,
        "missing_writer",
        lambda temp: rewrite_tsv(
            temp / LEDGER_REL / "writer-inventory.tsv",
            lambda lines: [line for line in lines if not line.startswith("W01\t")],
        ),
        "writer inventory total/parity",
    )

    def duplicate_authority(temp: Path) -> None:
        path = temp / LEDGER_REL / "owner-matrix.tsv"
        lines = path.read_text(encoding="utf-8").splitlines()
        duplicate = lines[1].replace("O01\t", "O03\t", 1)
        path.write_text("\n".join(lines + [duplicate]) + "\n", encoding="utf-8")

    expect_rejection(
        root,
        "duplicate_authority",
        duplicate_authority,
        "duplicate or missing authority",
    )
    expect_rejection(
        root,
        "missing_migration_state",
        lambda temp: rewrite_tsv(
            temp / LEDGER_REL / "migration-state-machine.tsv",
            lambda lines: [line for line in lines if not line.startswith("M4\t")],
        ),
        "migration state completeness/order",
    )

    def permanent_fallback(temp: Path) -> None:
        path = temp / LEDGER_REL / "temporary-compatibility-removal-ledger.tsv"
        lines = path.read_text(encoding="utf-8").splitlines()
        fields = lines[1].split("\t")
        fields[-1] = "YES"
        lines[1] = "\t".join(fields)
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    expect_rejection(
        root,
        "permanent_fallback",
        permanent_fallback,
        "permanent compatibility/fallback forbidden",
    )
    expect_rejection(
        root,
        "missing_guard",
        lambda temp: rewrite_tsv(
            temp / LEDGER_REL / "future-guard-red-control-plan.tsv",
            lambda lines: [
                line for line in lines
                if not line.startswith("BAN_WEB_DIRECT_PHYSICAL_PLACEMENT_AUTHORITY\t")
            ],
        ),
        "future guard completeness",
    )
    print("SELF_TEST_RESULT=PASS")


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="prove rejection of five required forbidden mutations",
    )
    args = parser.parse_args()
    root = repository_root()
    try:
        if args.self_test:
            self_test(root)
        else:
            validate(root)
            print("STORAGE_OBJECT_IDENTITY_PLACEMENT_MIGRATION_CONTRACT_VALIDATION=PASS")
    except (OSError, ValidationFailure) as failure:
        print(f"STORAGE_OBJECT_IDENTITY_PLACEMENT_MIGRATION_CONTRACT_VALIDATION=FAIL\n{failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
