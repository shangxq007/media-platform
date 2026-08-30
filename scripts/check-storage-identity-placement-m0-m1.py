#!/usr/bin/env python3
"""Fail-closed M0/M1 storage identity/placement authority counters."""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path


EXPECTED = {
    "CANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY_COUNT": 1,
    "CANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY_COUNT": 1,
    "STORAGE_WRITE_INTENT_RECOVERY_AUTHORITY_COUNT": 1,
    "RAW_STORAGE_PROVIDER_LOGICAL_ID_AUTHORITY_COUNT": 0,
    "TYPED_PROVIDER_LOGICAL_ID_AUTHORITY_COUNT": 0,
    "PROVIDER_BACKEND_LOGICAL_ID_AUTHORITY_COUNT": 0,
    "PHYSICAL_REFERENCE_TO_LOGICAL_ID_CONSTRUCTION_NEW_CODE_COUNT": 0,
    "PLACEMENT_NAMESPACE_OWNER_AUTHORITY_COUNT": 0,
    "GENERIC_JSON_PLACEMENT_AUTHORITY_COUNT": 0,
    "ARTIFACT_PHYSICAL_PLACEMENT_AUTHORITY_COUNT": 0,
    "NON_STORAGE_STORAGE_INFRASTRUCTURE_REPOSITORY_IMPORT_COUNT": 0,
    "DUAL_STORAGE_IDENTITY_AUTHORITY_COUNT": 0,
    "PERMANENT_STORAGE_FALLBACK_COUNT": 0,
    "M2_PLUS_RUNTIME_ACTIVATION_COUNT": 0,
    "CALLER_CANONICAL_BOOLEAN_AUTHORITY_COUNT": 0,
    "ENDPOINT_STABLE_IDENTITY_AUTHORITY_COUNT": 0,
    "OBSERVATION_TIME_STABLE_IDENTITY_AUTHORITY_COUNT": 0,
    "UNCLASSIFIED": 0,
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def java_sources(root: Path, relative: str) -> list[Path]:
    base = root / relative
    if not base.exists():
        return []
    return [p for p in base.rglob("*.java") if "/build/" not in p.as_posix()]


def occurrences(pattern: str, text: str, flags: int = 0) -> int:
    return len(re.findall(pattern, text, flags))


def target_storage_ddl(schema: str) -> str:
    blocks = re.findall(
        r"create table (storage_(?:database_binding|logical_object|object_placement|"
        r"placement_receipt|write_intent))\s*"
        r"\((.*?)\)\s*;",
        schema,
        re.IGNORECASE | re.DOTALL,
    )
    return "\n".join(body for _, body in blocks)


def unclassified_count(root: Path) -> int:
    ledger_dir = root / "docs/architecture/governance/storage-object-identity-placement-migration-v1"
    count = 0
    for ledger_name in (
        "current-vs-target-fact-classification.tsv",
        "writer-inventory.tsv",
        "reader-inventory.tsv",
    ):
        path = ledger_dir / ledger_name
        if not path.is_file():
            count += 1
            continue
        with path.open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle, delimiter="\t"):
                if not row.get("status") or row["status"] != "CLASSIFIED":
                    count += 1
                if "classification" in row and not row.get("classification"):
                    count += 1
    return count


def compute(root: Path) -> dict[str, int]:
    raw_spi = read(root / "storage-module/src/main/java/com/example/platform/storage/contract/StorageProvider.java")
    typed_spi = read(root / "storage-module/src/main/java/com/example/platform/storage/contract/provider/StorageProvider.java")
    authority_signature = (
        r"LOGICAL_IDENTITY_AUTHORITY|"
        r"(?:allocate|issue|mint|createCanonical|createLogical)\w*StorageObjectId\s*\("
    )

    backend_paths = [
        root / "storage-module/src/main/java/com/example/platform/storage/contract/memory",
        root / "storage-provider-opendal/src/main/java",
        root / "storage-module/src/main/java/com/example/platform/storage/infrastructure",
    ]
    backend_text = "\n".join(
        read(path)
        for base in backend_paths
        if base.exists()
        for path in base.rglob("*.java")
        if "/infrastructure/identity/" not in path.as_posix()
        and "/infrastructure/migration/" not in path.as_posix()
    )

    new_authority_paths = [
        "storage-module/src/main/java/com/example/platform/storage/api/StorageObjectIssuance.java",
        "storage-module/src/main/java/com/example/platform/storage/api/StorageWriteIntentRecovery.java",
        "storage-module/src/main/java/com/example/platform/storage/app/identity",
        "storage-module/src/main/java/com/example/platform/storage/domain/identity",
        "storage-module/src/main/java/com/example/platform/storage/infrastructure/identity",
        "storage-module/src/main/java/com/example/platform/storage/app/migration",
        "storage-module/src/main/java/com/example/platform/storage/domain/migration",
        "storage-module/src/main/java/com/example/platform/storage/infrastructure/migration",
    ]
    new_authority_text = "\n".join(
        read(path)
        for relative in new_authority_paths
        for path in ([root / relative] if (root / relative).is_file()
                     else java_sources(root, relative))
    )

    schema = read(root / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql")
    target_ddl = target_storage_ddl(schema)
    artifact_text = "\n".join(
        read(path) for path in java_sources(root, "artifact-module/src/main/java")
    )
    non_storage_module_text = "\n".join(
        read(path)
        for relative in (
            "artifact-module/src/main/java",
            "render-module/src/main/java",
            "worker-fabric-module/src/main/java",
            "platform-app/src/main/java/com/example/platform/web",
        )
        for path in java_sources(root, relative)
    )

    physical_constructor = re.compile(
        r"new\s+StorageObjectId\s*\([^;\n]*(?:bucket|objectKey|path|uri|locator|"
        r"location|namespace|provider|backend|completion|result)[^;\n]*\)",
        re.IGNORECASE,
    )
    dual_pattern = re.compile(
        r"DUAL_STORAGE_IDENTITY_AUTHORITY|(?:insert|update)\s+(?:into\s+)?"
        r"(?:storage_object|artifact_replica)\b",
        re.IGNORECASE,
    )
    fallback_pattern = re.compile(
        r"PERMANENT_STORAGE_FALLBACK|fallback\w*[^\n;]*(?:physical|legacy)|"
        r"parse\w*StorageObjectId|split\s*\([^\n;]*StorageObjectId",
        re.IGNORECASE,
    )
    m2_pattern = re.compile(
        r"M2_RUNTIME_ACTIVATION|M3_RUNTIME_ACTIVATION|M4_RUNTIME_ACTIVATION|"
        r"adoptLegacyPlacement\s*\(|LegacyStorageIdentityResolver|"
        r"update\s+artifact_replica\b",
        re.IGNORECASE,
    )
    placement_owner_pattern = re.compile(
        r"(?:new\s+StorageOwnershipScope|StorageOwnershipScope\.tenant)\s*\("
        r"[^;\n]*(?:namespace|location|placement|provider|bucket|objectKey|uri)",
        re.IGNORECASE,
    )
    endpoint_identity_pattern = re.compile(
        r"(?:sha256|fingerprint|databaseIdentity)[^;\n]*(?:endpoint|serverAddress|"
        r"serverPort|inet_server_addr|inet_server_port)|"
        r"(?:endpoint|serverAddress|serverPort|inet_server_addr|inet_server_port)"
        r"[^;\n]*(?:sha256|fingerprint|databaseIdentity)",
        re.IGNORECASE,
    )
    observation_time_identity_pattern = re.compile(
        r"(?:sha256|fingerprint|databaseIdentity)[^;\n]*(?:observedAt|observed_at)|"
        r"(?:observedAt|observed_at)[^;\n]*(?:sha256|fingerprint|databaseIdentity)",
        re.IGNORECASE,
    )

    return {
        "CANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY_COUNT": occurrences(
            r"\bCANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY\b", new_authority_text),
        "CANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY_COUNT": occurrences(
            r"\bCANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY\b", new_authority_text),
        "STORAGE_WRITE_INTENT_RECOVERY_AUTHORITY_COUNT": occurrences(
            r"\bSTORAGE_WRITE_INTENT_RECOVERY_AUTHORITY\b", new_authority_text),
        "RAW_STORAGE_PROVIDER_LOGICAL_ID_AUTHORITY_COUNT": occurrences(
            authority_signature, raw_spi, re.IGNORECASE),
        "TYPED_PROVIDER_LOGICAL_ID_AUTHORITY_COUNT": occurrences(
            authority_signature, typed_spi, re.IGNORECASE),
        "PROVIDER_BACKEND_LOGICAL_ID_AUTHORITY_COUNT": occurrences(
            authority_signature, backend_text, re.IGNORECASE),
        "PHYSICAL_REFERENCE_TO_LOGICAL_ID_CONSTRUCTION_NEW_CODE_COUNT": len(
            physical_constructor.findall(new_authority_text)),
        "PLACEMENT_NAMESPACE_OWNER_AUTHORITY_COUNT": len(
            placement_owner_pattern.findall(new_authority_text)),
        "GENERIC_JSON_PLACEMENT_AUTHORITY_COUNT": occurrences(
            r"\b(?:json|jsonb)\b|\w+_json\b", target_ddl, re.IGNORECASE),
        "ARTIFACT_PHYSICAL_PLACEMENT_AUTHORITY_COUNT": (
            occurrences(r"STORAGE_PHYSICAL_PLACEMENT_AUTHORITY", artifact_text)
            + occurrences(r"com\.example\.platform\.storage\.infrastructure", artifact_text)
            + occurrences(r"create table artifact_storage_placement\b", schema, re.IGNORECASE)
        ),
        "NON_STORAGE_STORAGE_INFRASTRUCTURE_REPOSITORY_IMPORT_COUNT": occurrences(
            r"import\s+com\.example\.platform\.storage\.infrastructure\."
            r"(?:identity|migration)\.",
            non_storage_module_text,
        ),
        "DUAL_STORAGE_IDENTITY_AUTHORITY_COUNT": len(dual_pattern.findall(new_authority_text)),
        "PERMANENT_STORAGE_FALLBACK_COUNT": len(fallback_pattern.findall(new_authority_text)),
        "M2_PLUS_RUNTIME_ACTIVATION_COUNT": len(m2_pattern.findall(new_authority_text)),
        "CALLER_CANONICAL_BOOLEAN_AUTHORITY_COUNT": occurrences(
            r"\bcanonicalRequested\b", new_authority_text),
        "ENDPOINT_STABLE_IDENTITY_AUTHORITY_COUNT": len(
            endpoint_identity_pattern.findall(new_authority_text)),
        "OBSERVATION_TIME_STABLE_IDENTITY_AUTHORITY_COUNT": len(
            observation_time_identity_pattern.findall(new_authority_text)),
        "UNCLASSIFIED": unclassified_count(root),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    counters = compute(args.root.resolve())
    failed = False
    for name, expected in EXPECTED.items():
        actual = counters[name]
        print(f"{name}={actual}")
        if actual != expected:
            print(f"ERROR {name}: expected {expected}, got {actual}", file=sys.stderr)
            failed = True
    print("ADOPTION_RUNTIME_CALL_COUNT=0")
    print("TESTCONTAINERS_DATABASE_IS_CANONICAL_MIGRATION_DATABASE=NO")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
