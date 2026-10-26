#!/usr/bin/env python3
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("fingerprint", Path(__file__).with_name("schema-fingerprint-v2.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

base = [
    {"object_kind": "relation", "schema": "public", "name": "a"},
    {"object_kind": "relation", "schema": "public", "name": "b"},
    {"object_kind": "column", "schema": "public", "relation": "a", "name": "id", "ordinal": 1, "type": "integer"},
    {"object_kind": "column", "schema": "public", "relation": "b", "name": "id", "ordinal": 1, "type": "integer"},
    {"object_kind": "index", "schema": "public", "relation": "a", "name": "a_pkey", "columns": ["id", "name"]},
]
_, digest = mod.fingerprint(base, 18, ["public"])
assert digest == mod.fingerprint(list(reversed(base)), 18, ["public"])[1]
assert b"Flyway" not in mod.fingerprint(base, 18, ["public"])[0]
assert mod.fingerprint(base + [{"object_kind": "column", "schema": "public", "relation": "a", "name": "x", "ordinal": 2}], 18, ["public"])[1] != digest

def must_fail(rows, major=18, scope=("public",)):
    try:
        mod.fingerprint(rows, major, list(scope))
        raise AssertionError(f"accepted: {rows!r} {major} {scope}")
    except ValueError:
        pass

must_fail(base, 16)
must_fail(base + [{"object_kind": "relation", "schema": "public", "name": "flyway_schema_history"}])
must_fail(base + [{"object_kind": "column", "schema": "public", "relation": "flyway_schema_history", "name": "x", "ordinal": 1}])
must_fail(base + [{"object_kind": "column", "schema": "other", "relation": "a", "name": "z", "ordinal": 9}])
must_fail(base + [{"object_kind": "column", "schema": "public", "relation": "a", "name": "id", "ordinal": 1}])
must_fail(base + [{"object_kind": "relation", "schema": "public", "name": "c", "bad": float("nan")}])

# Unknown metadata fields are rejected, including OID/ACL.
must_fail(base + [{"object_kind": "relation", "schema": "public", "name": "c", "oid": 1}])
must_fail(base + [{"object_kind": "relation", "schema": "public", "name": "c", "relacl": "x"}])
must_fail(base + [{"object_kind": "relation", "schema": "public", "name": "c", "attrelid": 1}])
print("schema-fingerprint-v2 strict tests: PASS")
