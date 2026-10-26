#!/usr/bin/env python3
"""Deterministic Schema Fingerprint V2 canonicalizer (no database required).

The database observer supplies catalog rows as JSON. This module owns strict
object identity, scope/exclusion enforcement, stable ordering, JSONL
serialization, and SHA-256. It does not connect to PostgreSQL or claim a live
fingerprint.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

ALGORITHM = "schema-fingerprint-v2"
VERSION = 2
HISTORY = "flyway_schema_history"
SPECS = {
    "schema": (("name",), ("schema", "name")),
    "relation": (("name",), ("schema", "name", "kind")),
    "column": (("relation", "name"), ("schema", "relation", "name", "ordinal", "type", "nullable", "default")),
    "constraint": (("relation", "name"), ("schema", "relation", "name", "definition", "kind", "columns")),
    "index": (("relation", "name"), ("schema", "relation", "name", "definition", "unique", "columns")),
    "sequence": (("name",), ("schema", "name", "data_type")),
    "function": (("name", "identity_args"), ("schema", "name", "identity_args", "definition", "language")),
    "trigger": (("relation", "name"), ("schema", "relation", "name", "definition", "enabled")),
    "extension": (("name",), ("schema", "name", "version")),
}
EXCLUDED_KEYS = {
    "oid", "tableoid", "relfilenode", "xmin", "cmin", "ctid",
    "acl", "aclitem", "data", "rows", "query", "owner", "tablespace",
}


def _reject_constant(value: str):
    raise ValueError(f"non-finite JSON number: {value}")


def _clean(value):
    if isinstance(value, dict):
        return {k: _clean(v) for k, v in value.items() if k not in EXCLUDED_KEYS}
    if isinstance(value, list):
        return [_clean(v) for v in value]
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError("non-finite number")
    return value


def canonical_record(record: dict, scope: set[str]) -> tuple[dict, tuple]:
    if not isinstance(record, dict):
        raise ValueError("record must be an object")
    kind = record.get("object_kind")
    if kind not in SPECS:
        raise ValueError(f"unsupported object kind: {kind!r}")
    for key in ("schema",) + SPECS[kind][0]:
        value = record.get(key)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{kind} missing required identity field {key}")
    if record["schema"] not in scope:
        raise ValueError(f"{kind} outside schema scope")
    if kind == "schema" and record["name"] not in scope:
        raise ValueError("schema outside scope")
    names = [record.get("schema"), record.get("name"), record.get("relation")]
    if HISTORY in names:
        raise ValueError("Flyway history is excluded")
    allowed = set(SPECS[kind][1]) | {"object_kind"}
    unknown = set(record) - allowed
    if unknown:
        raise ValueError(f"{kind} contains unknown metadata fields: {sorted(unknown)}")
    cleaned = _clean(record)
    if kind in ("column", "constraint", "index", "trigger"):
        identity = (kind, cleaned["schema"], cleaned["relation"], cleaned["name"])
    else:
        identity = (kind, cleaned["schema"], cleaned["name"])
    return cleaned, identity


def canonical_lines(records: list[dict], server_major: int, schema_scope: list[str]) -> bytes:
    if server_major != 18:
        raise ValueError("schema-fingerprint-v2 requires PostgreSQL major 18")
    if not schema_scope or any(not isinstance(s, str) or not s for s in schema_scope):
        raise ValueError("schema scope must be non-empty")
    scope = set(schema_scope)
    normalized = []
    identities = set()
    for record in records:
        cleaned, identity = canonical_record(record, scope)
        if identity in identities:
            raise ValueError(f"duplicate object identity: {identity}")
        identities.add(identity)
        normalized.append((identity, cleaned))
    normalized.sort(key=lambda item: (item[0], str(item[1].get("ordinal", ""))))
    header = {
        "algorithm": ALGORITHM,
        "version": VERSION,
        "server_major": server_major,
        "schema_scope": sorted(scope),
        "excluded": ["flyway_schema_history", "data", "oids", "acl"],
    }
    lines = [json.dumps(header, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)]
    for _, record in normalized:
        lines.append(json.dumps(record, ensure_ascii=False, sort_keys=True,
                                separators=(",", ":"), allow_nan=False))
    return ("\n".join(lines) + "\n").encode("utf-8")


def fingerprint(records: list[dict], server_major: int, schema_scope: list[str]) -> tuple[bytes, str]:
    manifest = canonical_lines(records, server_major, schema_scope)
    return manifest, hashlib.sha256(manifest).hexdigest()


def load_rows(path: str) -> list[dict]:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"), parse_constant=_reject_constant)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        raise ValueError(f"invalid input: {exc}") from exc


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--server-major", type=int, required=True)
    parser.add_argument("--schema", action="append", required=True)
    args = parser.parse_args()
    rows = load_rows(args.input)
    if not isinstance(rows, list):
        raise SystemExit("input must be a JSON array")
    manifest, digest = fingerprint(rows, args.server_major, args.schema)
    Path(args.output).write_bytes(manifest)
    Path(args.output + ".sha256").write_text(digest + "  " + args.output + "\n", encoding="utf-8")
    print(digest)


if __name__ == "__main__":
    main()
