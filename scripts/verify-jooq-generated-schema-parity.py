#!/usr/bin/env python3
"""Verify canonical V1 identities and bounded owner keys against tracked jOOQ.

CANONICAL_SCHEMA_DEFINES_GENERATED_SCHEMA_EXPECTATION_V1:

* expected identities come only from complete ``CREATE TABLE name (...) ;``
  declarations in the canonical migration;
* generated table identities come from the no-argument table constructors'
  ``DSL.name("name")`` values; and
* generated record identities come from ``super(TableClass.CONSTANT)`` and are
  resolved through those parsed table classes; and
* the two storage owner-idempotency constraints and their generated
  ``Keys.java`` declarations must contain the same exact ordered columns.

This intentionally maintains no expected count, identity list, or baseline.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
import re
import sys
from typing import Iterable, Sequence


class VerificationError(ValueError):
    """A fail-closed schema or generated-source verification failure."""


@dataclass(frozen=True)
class SqlToken:
    kind: str
    value: str
    offset: int


@dataclass(frozen=True)
class GeneratedTable:
    identity: str
    class_name: str
    singleton_name: str
    record_class_name: str
    path: Path


@dataclass(frozen=True)
class GeneratedRecord:
    identity: str
    class_name: str
    table_class_name: str
    path: Path


@dataclass(frozen=True)
class OwnerUniqueKeySpec:
    constraint_name: str
    table_name: str
    generated_symbol: str
    generated_record_class: str
    generated_table_class: str
    generated_table_singleton: str
    columns: tuple[str, ...]


@dataclass(frozen=True)
class CanonicalOwnerUniqueKey:
    constraint_name: str
    table_name: str
    columns: tuple[str, ...]


@dataclass(frozen=True)
class GeneratedOwnerUniqueKey:
    constraint_name: str
    symbol: str
    record_class: str
    table_class: str
    table_singleton: str
    fields: tuple[str, ...]


_JAVA_IDENTIFIER = r"[A-Za-z_$][A-Za-z0-9_$]*"
_SQL_IDENTITY = re.compile(r"[A-Za-z_][A-Za-z0-9_$]*\Z")
_OWNER_COLUMNS = ("tenant_id", "project_id", "issuance_idempotency_key")
_OWNER_UNIQUE_KEY_SPECS = (
    OwnerUniqueKeySpec(
        constraint_name="uq_storage_logical_object_owner_idempotency",
        table_name="storage_logical_object",
        generated_symbol="UQ_STORAGE_LOGICAL_OBJECT_OWNER_IDEMPOTENCY",
        generated_record_class="StorageLogicalObjectRecord",
        generated_table_class="StorageLogicalObject",
        generated_table_singleton="STORAGE_LOGICAL_OBJECT",
        columns=_OWNER_COLUMNS,
    ),
    OwnerUniqueKeySpec(
        constraint_name="uq_storage_write_intent_owner_idempotency",
        table_name="storage_write_intent",
        generated_symbol="UQ_STORAGE_WRITE_INTENT_OWNER_IDEMPOTENCY",
        generated_record_class="StorageWriteIntentRecord",
        generated_table_class="StorageWriteIntent",
        generated_table_singleton="STORAGE_WRITE_INTENT",
        columns=_OWNER_COLUMNS,
    ),
)
_OWNER_CONSTRAINT_NAMES = frozenset(
    spec.constraint_name for spec in _OWNER_UNIQUE_KEY_SPECS
)


def _sql_error(message: str, offset: int) -> VerificationError:
    return VerificationError(f"CANONICAL_SQL_UNRECOGNIZED offset={offset}: {message}")


def lex_sql(sql: str) -> list[SqlToken]:
    """Lex enough PostgreSQL to find declarations without trusting raw regexes."""
    tokens: list[SqlToken] = []
    index = 0
    length = len(sql)
    while index < length:
        char = sql[index]
        if char.isspace():
            index += 1
            continue
        if sql.startswith("--", index):
            newline = sql.find("\n", index + 2)
            index = length if newline < 0 else newline + 1
            continue
        if sql.startswith("/*", index):
            start = index
            index += 2
            depth = 1
            while depth:
                if index >= length:
                    raise _sql_error("unterminated block comment", start)
                if sql.startswith("/*", index):
                    depth += 1
                    index += 2
                elif sql.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            continue
        if char == "'":
            start = index
            index += 1
            while True:
                if index >= length:
                    raise _sql_error("unterminated string literal", start)
                if sql[index] == "'":
                    if index + 1 < length and sql[index + 1] == "'":
                        index += 2
                    else:
                        index += 1
                        break
                else:
                    index += 1
            continue
        if char == '"':
            start = index
            index += 1
            value: list[str] = []
            while True:
                if index >= length:
                    raise _sql_error("unterminated quoted identifier", start)
                if sql[index] == '"':
                    if index + 1 < length and sql[index + 1] == '"':
                        value.append('"')
                        index += 2
                    else:
                        index += 1
                        break
                else:
                    value.append(sql[index])
                    index += 1
            identity = "".join(value)
            if not identity:
                raise _sql_error("empty quoted identifier", start)
            tokens.append(SqlToken("IDENT", identity, start))
            continue
        if char == "$":
            tag_match = re.match(r"\$[A-Za-z_][A-Za-z0-9_]*\$|\$\$", sql[index:])
            if tag_match:
                start = index
                tag = tag_match.group(0)
                index += len(tag)
                end = sql.find(tag, index)
                if end < 0:
                    raise _sql_error("unterminated dollar-quoted string", start)
                index = end + len(tag)
                continue
        if char.isalpha() or char == "_":
            start = index
            index += 1
            while index < length and (sql[index].isalnum() or sql[index] in "_$"):
                index += 1
            tokens.append(SqlToken("WORD", sql[start:index], start))
            continue
        tokens.append(SqlToken(char, char, index))
        index += 1
    return tokens


def _is_word(token: SqlToken, expected: str) -> bool:
    return token.kind == "WORD" and token.value.casefold() == expected


def _parse_create_table_statement(tokens: Sequence[SqlToken], terminated: bool) -> str:
    start = tokens[0].offset
    if not terminated:
        raise _sql_error("CREATE TABLE declaration is not semicolon-terminated", start)
    if len(tokens) < 5 or not _is_word(tokens[0], "create") or not _is_word(tokens[1], "table"):
        raise _sql_error(
            "only CREATE TABLE <identifier> (...) declarations are recognized", start
        )
    name = tokens[2]
    if name.kind not in {"WORD", "IDENT"}:
        raise _sql_error("CREATE TABLE is missing a valid table identifier", name.offset)
    if name.kind == "WORD" and not _SQL_IDENTITY.fullmatch(name.value):
        raise _sql_error("invalid unquoted table identifier", name.offset)
    identity = name.value.casefold() if name.kind == "WORD" else name.value
    if tokens[3].kind != "(":
        raise _sql_error("CREATE TABLE identifier must be followed by '('", tokens[3].offset)

    depth = 0
    closing_index: int | None = None
    for token_index, token in enumerate(tokens[3:], start=3):
        if token.kind == "(":
            depth += 1
        elif token.kind == ")":
            depth -= 1
            if depth < 0:
                raise _sql_error("unmatched ')' in CREATE TABLE", token.offset)
            if depth == 0:
                closing_index = token_index
                break
    if closing_index is None:
        raise _sql_error("CREATE TABLE has no matching closing ')'", start)
    if closing_index != len(tokens) - 1:
        raise _sql_error("tokens after CREATE TABLE closing ')' are not recognized", tokens[closing_index + 1].offset)
    return identity


def _sql_statements(sql: str) -> list[tuple[list[SqlToken], bool]]:
    statements: list[tuple[list[SqlToken], bool]] = []
    current: list[SqlToken] = []
    for token in lex_sql(sql):
        if token.kind == ";":
            if current:
                statements.append((current, True))
                current = []
        else:
            current.append(token)
    if current:
        statements.append((current, False))
    return statements


def parse_canonical_table_identities(sql: str) -> list[str]:
    """Parse all and only the canonical migration's bounded CREATE TABLE grammar."""
    statements = _sql_statements(sql)

    identities: list[str] = []
    for tokens, terminated in statements:
        create_positions = [
            index
            for index in range(len(tokens) - 1)
            if _is_word(tokens[index], "create") and _is_word(tokens[index + 1], "table")
        ]
        starts_with_create = bool(tokens) and _is_word(tokens[0], "create")
        contains_table = any(_is_word(token, "table") for token in tokens)
        if create_positions:
            if create_positions != [0]:
                raise _sql_error("CREATE TABLE must begin its own statement", tokens[create_positions[0]].offset)
            identities.append(_parse_create_table_statement(tokens, terminated))
        elif starts_with_create and contains_table:
            raise _sql_error(
                "unrecognized CREATE ... TABLE declaration; canonical grammar is CREATE TABLE <identifier> (...) ;",
                tokens[0].offset,
            )

    _reject_duplicates_or_empty("CANONICAL_TABLE", identities)
    return identities


def _sql_identifier(token: SqlToken, context: str) -> str:
    if token.kind not in {"WORD", "IDENT"}:
        raise _sql_error(f"{context} requires an identifier", token.offset)
    if token.kind == "WORD" and not _SQL_IDENTITY.fullmatch(token.value):
        raise _sql_error(f"{context} has an invalid unquoted identifier", token.offset)
    return token.value.casefold() if token.kind == "WORD" else token.value


def _parse_target_owner_constraint(
    tokens: Sequence[SqlToken],
    constraint_index: int,
    table_name: str,
) -> CanonicalOwnerUniqueKey | None:
    if constraint_index + 1 >= len(tokens):
        raise _sql_error("CONSTRAINT is missing its name", tokens[constraint_index].offset)
    constraint_name = _sql_identifier(
        tokens[constraint_index + 1], "CONSTRAINT name"
    )
    if constraint_name not in _OWNER_CONSTRAINT_NAMES:
        return None

    cursor = constraint_index + 2
    if cursor >= len(tokens) or not _is_word(tokens[cursor], "unique"):
        offset = (
            tokens[cursor].offset
            if cursor < len(tokens)
            else tokens[constraint_index].offset
        )
        raise _sql_error(
            f"target owner constraint {constraint_name} is not a named UNIQUE constraint",
            offset,
        )
    cursor += 1
    if cursor < len(tokens) and _is_word(tokens[cursor], "nulls"):
        if (
            cursor + 2 >= len(tokens)
            or not _is_word(tokens[cursor + 1], "not")
            or not _is_word(tokens[cursor + 2], "distinct")
        ):
            raise _sql_error(
                f"target owner constraint {constraint_name} has unrecognized NULLS syntax",
                tokens[cursor].offset,
            )
        cursor += 3
    if cursor >= len(tokens) or tokens[cursor].kind != "(":
        offset = (
            tokens[cursor].offset
            if cursor < len(tokens)
            else tokens[constraint_index].offset
        )
        raise _sql_error(
            f"target owner constraint {constraint_name} is missing its column list",
            offset,
        )
    cursor += 1

    columns: list[str] = []
    expect_column = True
    while cursor < len(tokens):
        token = tokens[cursor]
        if expect_column:
            if token.kind == ")":
                if not columns:
                    raise _sql_error(
                        f"target owner constraint {constraint_name} has no columns",
                        token.offset,
                    )
                break
            columns.append(_sql_identifier(token, "UNIQUE column"))
            expect_column = False
        elif token.kind == ",":
            expect_column = True
        elif token.kind == ")":
            break
        else:
            raise _sql_error(
                f"target owner constraint {constraint_name} has an unrecognized column list",
                token.offset,
            )
        cursor += 1
    else:
        raise _sql_error(
            f"target owner constraint {constraint_name} has an unterminated column list",
            tokens[constraint_index].offset,
        )
    if expect_column:
        raise _sql_error(
            f"target owner constraint {constraint_name} has a trailing comma",
            tokens[cursor].offset,
        )
    return CanonicalOwnerUniqueKey(constraint_name, table_name, tuple(columns))


def parse_canonical_owner_unique_keys(
    sql: str,
) -> dict[str, CanonicalOwnerUniqueKey]:
    """Parse the named canonical owner keys without trusting comments or literals."""
    parsed: dict[str, CanonicalOwnerUniqueKey] = {}
    for tokens, terminated in _sql_statements(sql):
        if not tokens or not _is_word(tokens[0], "create"):
            continue
        contains_table = any(_is_word(token, "table") for token in tokens)
        if not contains_table:
            continue
        table_name = _parse_create_table_statement(tokens, terminated)
        depth = 0
        for index, token in enumerate(tokens):
            if token.kind == "(":
                depth += 1
            elif token.kind == ")":
                depth -= 1
            elif depth == 1 and _is_word(token, "constraint"):
                owner_key = _parse_target_owner_constraint(tokens, index, table_name)
                if owner_key is None:
                    continue
                if owner_key.constraint_name in parsed:
                    raise VerificationError(
                        "OWNER_UNIQUE_KEY_PARITY_CANONICAL_DUPLICATE "
                        f"constraint={owner_key.constraint_name}"
                    )
                parsed[owner_key.constraint_name] = owner_key
    return parsed


def _exactly_one(pattern: str, source: str, code: str, path: Path, flags: int = 0) -> re.Match[str]:
    matches = list(re.finditer(pattern, source, flags))
    if len(matches) != 1:
        raise VerificationError(f"{code} path={path}: expected exactly one match, found {len(matches)}")
    return matches[0]


def parse_generated_table(path: Path) -> GeneratedTable:
    source = path.read_text(encoding="utf-8")
    declaration = _exactly_one(
        rf"public\s+class\s+({_JAVA_IDENTIFIER})\s+extends\s+TableImpl<({_JAVA_IDENTIFIER})>",
        source,
        "GENERATED_TABLE_DECLARATION_UNRECOGNIZED",
        path,
    )
    class_name, declared_record = declaration.groups()
    if path.stem != class_name:
        raise VerificationError(
            f"GENERATED_TABLE_FILENAME_MISMATCH path={path}: class={class_name}"
        )

    record_type = _exactly_one(
        rf"public\s+Class<({_JAVA_IDENTIFIER})>\s+getRecordType\s*\(\s*\)\s*\{{\s*"
        rf"return\s+({_JAVA_IDENTIFIER})\.class\s*;\s*\}}",
        source,
        "GENERATED_TABLE_RECORD_TYPE_UNRECOGNIZED",
        path,
        re.DOTALL,
    )
    if record_type.group(1) != declared_record or record_type.group(2) != declared_record:
        raise VerificationError(
            f"GENERATED_TABLE_RECORD_TYPE_MISMATCH path={path}: declared={declared_record} returned={record_type.groups()}"
        )

    constructor = _exactly_one(
        rf"public\s+{re.escape(class_name)}\s*\(\s*\)\s*\{{\s*"
        r'this\s*\(\s*DSL\.name\s*\(\s*"([A-Za-z_][A-Za-z0-9_$]*)"\s*\)\s*,\s*null\s*\)\s*;\s*\}',
        source,
        "GENERATED_TABLE_IDENTITY_UNRECOGNIZED",
        path,
        re.DOTALL,
    )
    singleton = _exactly_one(
        rf"public\s+static\s+final\s+{re.escape(class_name)}\s+({_JAVA_IDENTIFIER})\s*=\s*"
        rf"new\s+{re.escape(class_name)}\s*\(\s*\)\s*;",
        source,
        "GENERATED_TABLE_SINGLETON_UNRECOGNIZED",
        path,
    )
    return GeneratedTable(
        identity=constructor.group(1),
        class_name=class_name,
        singleton_name=singleton.group(1),
        record_class_name=declared_record,
        path=path,
    )


def parse_generated_record(
    path: Path, tables_by_binding: dict[tuple[str, str], GeneratedTable]
) -> GeneratedRecord:
    source = path.read_text(encoding="utf-8")
    declaration = _exactly_one(
        rf"public\s+class\s+({_JAVA_IDENTIFIER})\s+extends\s+"
        rf"(?:UpdatableRecordImpl|TableRecordImpl)<({_JAVA_IDENTIFIER})>",
        source,
        "GENERATED_RECORD_DECLARATION_UNRECOGNIZED",
        path,
    )
    class_name, generic_record = declaration.groups()
    if path.stem != class_name or generic_record != class_name:
        raise VerificationError(
            f"GENERATED_RECORD_CLASS_MISMATCH path={path}: class={class_name} generic={generic_record}"
        )

    bindings = set(
        re.findall(rf"\bsuper\s*\(\s*({_JAVA_IDENTIFIER})\.({_JAVA_IDENTIFIER})\s*\)\s*;", source)
    )
    if len(bindings) != 1:
        raise VerificationError(
            f"GENERATED_RECORD_BINDING_UNRECOGNIZED path={path}: distinct_bindings={sorted(bindings)}"
        )
    binding = next(iter(bindings))
    table = tables_by_binding.get(binding)
    if table is None:
        raise VerificationError(
            f"GENERATED_RECORD_UNEXPECTED_TABLE_BINDING path={path}: binding={binding[0]}.{binding[1]}"
        )
    if table.record_class_name != class_name:
        raise VerificationError(
            f"GENERATED_RECORD_TABLE_TYPE_MISMATCH path={path}: table={table.class_name} expects={table.record_class_name} found={class_name}"
        )
    return GeneratedRecord(table.identity, class_name, table.class_name, path)


_GENERATED_UNIQUE_KEY_DECLARATION = re.compile(
    rf"^[ \t]*public\s+static\s+final\s+UniqueKey<(?P<record>{_JAVA_IDENTIFIER})>\s+"
    rf"(?P<symbol>{_JAVA_IDENTIFIER})\s*=\s*Internal\.createUniqueKey\("
    rf"(?P<table>{_JAVA_IDENTIFIER})\.(?P<singleton>{_JAVA_IDENTIFIER})\s*,\s*"
    r'DSL\.name\("(?P<constraint>[A-Za-z_][A-Za-z0-9_$]*)"\)\s*,\s*'
    r"new\s+TableField\[\]\s*\{(?P<fields>[^}]*)\}\s*,\s*true\s*\)\s*;[ \t]*$",
    re.MULTILINE,
)


def parse_generated_owner_unique_keys(
    keys_path: Path,
) -> dict[str, GeneratedOwnerUniqueKey]:
    """Parse exact target declarations from generated ``Keys.java``."""
    if not keys_path.is_file():
        raise VerificationError(
            f"OWNER_UNIQUE_KEY_PARITY_GENERATED_KEYS_MISSING path={keys_path}"
        )
    source = keys_path.read_text(encoding="utf-8")
    matches_by_symbol: dict[str, list[re.Match[str]]] = {
        spec.generated_symbol: [] for spec in _OWNER_UNIQUE_KEY_SPECS
    }
    for match in _GENERATED_UNIQUE_KEY_DECLARATION.finditer(source):
        if match.group("symbol") in matches_by_symbol:
            matches_by_symbol[match.group("symbol")].append(match)

    parsed: dict[str, GeneratedOwnerUniqueKey] = {}
    for spec in _OWNER_UNIQUE_KEY_SPECS:
        matches = matches_by_symbol[spec.generated_symbol]
        if len(matches) != 1:
            raise VerificationError(
                "OWNER_UNIQUE_KEY_PARITY_GENERATED_DECLARATION_UNRECOGNIZED "
                f"symbol={spec.generated_symbol} path={keys_path} "
                f"expected=1 actual={len(matches)}"
            )
        match = matches[0]
        field_references = [
            field.strip() for field in match.group("fields").split(",")
        ]
        if not field_references or any(not field for field in field_references):
            raise VerificationError(
                "OWNER_UNIQUE_KEY_PARITY_GENERATED_FIELDS_UNRECOGNIZED "
                f"symbol={spec.generated_symbol} path={keys_path}"
            )
        fields: list[str] = []
        for field_reference in field_references:
            field_match = re.fullmatch(
                rf"({_JAVA_IDENTIFIER})\.({_JAVA_IDENTIFIER})\.({_JAVA_IDENTIFIER})",
                field_reference,
            )
            if field_match is None:
                raise VerificationError(
                    "OWNER_UNIQUE_KEY_PARITY_GENERATED_FIELD_UNRECOGNIZED "
                    f"symbol={spec.generated_symbol} field={field_reference} path={keys_path}"
                )
            field_table, field_singleton, field_name = field_match.groups()
            if (
                field_table != match.group("table")
                or field_singleton != match.group("singleton")
            ):
                raise VerificationError(
                    "OWNER_UNIQUE_KEY_PARITY_GENERATED_FIELD_BINDING_MISMATCH "
                    f"symbol={spec.generated_symbol} field={field_reference} "
                    f"binding={match.group('table')}.{match.group('singleton')}"
                )
            fields.append(field_name)

        owner_key = GeneratedOwnerUniqueKey(
            constraint_name=match.group("constraint"),
            symbol=match.group("symbol"),
            record_class=match.group("record"),
            table_class=match.group("table"),
            table_singleton=match.group("singleton"),
            fields=tuple(fields),
        )
        if owner_key.constraint_name in parsed:
            raise VerificationError(
                "OWNER_UNIQUE_KEY_PARITY_GENERATED_CONSTRAINT_DUPLICATE "
                f"constraint={owner_key.constraint_name} path={keys_path}"
            )
        parsed[owner_key.constraint_name] = owner_key
    return parsed


def verify_owner_unique_key_parity(sql: str, keys_path: Path) -> None:
    canonical = parse_canonical_owner_unique_keys(sql)
    generated = parse_generated_owner_unique_keys(keys_path)
    errors: list[str] = []
    for spec in _OWNER_UNIQUE_KEY_SPECS:
        canonical_key = canonical.get(spec.constraint_name)
        generated_key = generated.get(spec.constraint_name)
        if canonical_key is None:
            errors.append(
                "OWNER_UNIQUE_KEY_PARITY_CANONICAL_MISSING "
                f"constraint={spec.constraint_name}"
            )
        else:
            if canonical_key.table_name != spec.table_name:
                errors.append(
                    "OWNER_UNIQUE_KEY_PARITY_CANONICAL_TABLE_MISMATCH "
                    f"constraint={spec.constraint_name} expected={spec.table_name} "
                    f"actual={canonical_key.table_name}"
                )
            if canonical_key.columns != spec.columns:
                errors.append(
                    "OWNER_UNIQUE_KEY_PARITY_CANONICAL_COLUMNS_MISMATCH "
                    f"constraint={spec.constraint_name} expected={spec.columns} "
                    f"actual={canonical_key.columns}"
                )

        if generated_key is None:
            errors.append(
                "OWNER_UNIQUE_KEY_PARITY_GENERATED_CONSTRAINT_MISSING "
                f"constraint={spec.constraint_name} symbol={spec.generated_symbol}"
            )
        else:
            expected_binding = (
                spec.generated_symbol,
                spec.generated_record_class,
                spec.generated_table_class,
                spec.generated_table_singleton,
            )
            actual_binding = (
                generated_key.symbol,
                generated_key.record_class,
                generated_key.table_class,
                generated_key.table_singleton,
            )
            if actual_binding != expected_binding:
                errors.append(
                    "OWNER_UNIQUE_KEY_PARITY_GENERATED_BINDING_MISMATCH "
                    f"constraint={spec.constraint_name} expected={expected_binding} "
                    f"actual={actual_binding}"
                )
            expected_fields = tuple(column.upper() for column in spec.columns)
            if generated_key.fields != expected_fields:
                errors.append(
                    "OWNER_UNIQUE_KEY_PARITY_GENERATED_FIELDS_MISMATCH "
                    f"constraint={spec.constraint_name} expected={expected_fields} "
                    f"actual={generated_key.fields}"
                )

        if canonical_key is not None and generated_key is not None:
            canonical_fields = tuple(
                column.upper() for column in canonical_key.columns
            )
            if canonical_fields != generated_key.fields:
                errors.append(
                    "OWNER_UNIQUE_KEY_PARITY_SCHEMA_GENERATED_DISAGREEMENT "
                    f"constraint={spec.constraint_name} schema={canonical_fields} "
                    f"generated={generated_key.fields}"
                )
    if errors:
        raise VerificationError("; ".join(errors))


def load_generated_identities(generated_root: Path) -> tuple[list[str], list[str], int]:
    tables_directory = generated_root / "tables"
    records_directory = tables_directory / "records"
    if not tables_directory.is_dir() or not records_directory.is_dir():
        raise VerificationError(
            f"GENERATED_SOURCE_DIRECTORY_MISSING root={generated_root}"
        )

    table_paths = sorted(tables_directory.glob("*.java"))
    record_paths = sorted(records_directory.glob("*.java"))
    if not table_paths:
        raise VerificationError("GENERATED_TABLE_UNIVERSE_EMPTY")
    if not record_paths:
        raise VerificationError("GENERATED_RECORD_UNIVERSE_EMPTY")

    tables = [parse_generated_table(path) for path in table_paths]
    _reject_duplicates_or_empty("GENERATED_TABLE", [table.identity for table in tables])
    class_names = Counter(table.class_name for table in tables)
    duplicate_classes = sorted(name for name, count in class_names.items() if count > 1)
    if duplicate_classes:
        raise VerificationError(f"GENERATED_TABLE_CLASS_DUPLICATE identities={duplicate_classes}")
    bindings = Counter((table.class_name, table.singleton_name) for table in tables)
    duplicate_bindings = sorted(binding for binding, count in bindings.items() if count > 1)
    if duplicate_bindings:
        raise VerificationError(f"GENERATED_TABLE_BINDING_DUPLICATE identities={duplicate_bindings}")
    tables_by_binding = {
        (table.class_name, table.singleton_name): table for table in tables
    }

    records = [parse_generated_record(path, tables_by_binding) for path in record_paths]
    _reject_duplicates_or_empty("GENERATED_RECORD", [record.identity for record in records])
    java_file_count = sum(1 for path in generated_root.rglob("*.java") if path.is_file())
    return (
        [table.identity for table in tables],
        [record.identity for record in records],
        java_file_count,
    )


def _reject_duplicates_or_empty(label: str, identities: Sequence[str]) -> None:
    if not identities:
        raise VerificationError(f"{label}_UNIVERSE_EMPTY")
    counts = Counter(identities)
    duplicates = sorted(identity for identity, count in counts.items() if count > 1)
    if duplicates:
        raise VerificationError(f"{label}_IDENTITY_DUPLICATE identities={duplicates}")


def verify_identity_parity(
    canonical_identities: Iterable[str],
    generated_table_identities: Iterable[str],
    generated_record_identities: Iterable[str],
) -> None:
    canonical = list(canonical_identities)
    tables = list(generated_table_identities)
    records = list(generated_record_identities)
    _reject_duplicates_or_empty("CANONICAL_TABLE", canonical)
    _reject_duplicates_or_empty("GENERATED_TABLE", tables)
    _reject_duplicates_or_empty("GENERATED_RECORD", records)

    expected = set(canonical)
    actual_tables = set(tables)
    actual_records = set(records)
    errors: list[str] = []
    for code, values in (
        ("MISSING_GENERATED_TABLE", expected - actual_tables),
        ("UNEXPECTED_GENERATED_TABLE", actual_tables - expected),
        ("MISSING_GENERATED_RECORD", expected - actual_records),
        ("UNEXPECTED_GENERATED_RECORD", actual_records - expected),
    ):
        if values:
            errors.append(f"{code} identities={sorted(values)}")
    if errors:
        raise VerificationError("; ".join(errors))


def verify(schema_path: Path, generated_root: Path) -> tuple[int, int, int, int]:
    if not schema_path.is_file():
        raise VerificationError(f"CANONICAL_SCHEMA_MISSING path={schema_path}")
    sql = schema_path.read_text(encoding="utf-8")
    canonical = parse_canonical_table_identities(sql)
    tables, records, java_files = load_generated_identities(generated_root)
    verify_identity_parity(canonical, tables, records)
    verify_owner_unique_key_parity(sql, generated_root / "Keys.java")
    return len(canonical), len(tables), len(records), java_files


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--schema", required=True, type=Path)
    parser.add_argument("--generated", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        canonical_count, table_count, record_count, java_file_count = verify(
            args.schema, args.generated
        )
    except (OSError, UnicodeError, VerificationError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("Generated source inventory:")
    print(f"  Canonical tables: {canonical_count}")
    print(f"  Generated table identities: {table_count}")
    print(f"  Generated record identities: {record_count}")
    print(f"  Total Java files: {java_file_count}")
    print("  Exact storage owner unique keys: 2")
    print("OK: CANONICAL_SCHEMA_DEFINES_GENERATED_SCHEMA_EXPECTATION_V1")
    print("OK: STORAGE_OWNER_UNIQUE_KEY_EXACT_ORDERED_PARITY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
