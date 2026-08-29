#!/usr/bin/env python3
"""Fail closed on unresolved values in the six Phase 19 governance artifacts."""

from __future__ import annotations

import argparse
from collections.abc import Iterator
import json
from pathlib import Path
import re
import string
import sys
from typing import TypeAlias


CAPABILITY_LEDGER = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-legacy-render-ffmpeg-functional-capability-ledger-v1.json"
)
CLEAN_FORWARD_LEDGER = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-render-zero-awareness-clean-forward-path-ledger-v2.json"
)
CORRECTION = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-render-module-ffmpeg-zero-awareness-correction.md"
)
RECONCILIATION = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-capability-disposition-reconciliation-v1.json"
)
TEST_SURFACE_ACCOUNTING = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-test-surface-change-accounting-v1.json"
)
SEMGREP_TARGET_ACCOUNTING = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json"
)
TARGET_ARTIFACTS = (
    CAPABILITY_LEDGER,
    CLEAN_FORWARD_LEDGER,
    CORRECTION,
    RECONCILIATION,
    TEST_SURFACE_ACCOUNTING,
    SEMGREP_TARGET_ACCOUNTING,
)
RECONCILIATION_HISTORICAL_IDENTIFIER = "PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT"

JsonPathPart: TypeAlias = str | int
JsonPath: TypeAlias = tuple[JsonPathPart, ...]

MARKER_PREFIX = re.compile(
    r"^(?:TODO|TBD|FIXME|PLACEHOLDER|XXX|<fill-me>)",
    re.IGNORECASE,
)
MASKED_VALUE = re.compile(r"^\*{3,}$")
STRUCTURED_PLACEHOLDER_IDENTIFIER = re.compile(
    r"^(?:TODO|TBD|FIXME|PLACEHOLDER|XXX)(?:[_-][A-Za-z0-9]+)+$",
    re.IGNORECASE,
)
JSON_IDENTIFIER_KEY = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
MARKDOWN_ASSIGNMENT = re.compile(
    r"^\s*(?:[-+*]\s+)?(?:\*\*|__)?[^|:=\n]+?(?:\*\*|__)?\s*[:=]\s*(.*?)\s*$"
)
MARKDOWN_PREFIX = re.compile(
    r"^(?:(?:>\s*)+|#{1,6}\s+|(?:[-+]|\*(?!\*))\s+)"
)
MARKDOWN_HORIZONTAL_RULE = re.compile(r"^(?:\*\s*){3,}$")


def is_unresolved_value(value: str) -> bool:
    """Return whether an entire semantic value is an unresolved marker."""

    candidate = value.strip()
    if MASKED_VALUE.fullmatch(candidate):
        return True
    marker = MARKER_PREFIX.match(candidate)
    if marker is None:
        return False
    remainder = candidate[marker.end():]
    return all(
        character.isspace() or character in string.punctuation
        for character in remainder
    )


def is_structured_placeholder_identifier(value: str) -> bool:
    """Catch marker-prefixed opaque identifiers unless structurally exempted."""

    return STRUCTURED_PLACEHOLDER_IDENTIFIER.fullmatch(value.strip()) is not None


def is_capability_key_exception(artifact: Path, path: JsonPath) -> bool:
    """Match only capability-ledger ``capabilities[*].CapabilityKey`` values."""

    return (
        artifact == CAPABILITY_LEDGER
        and len(path) == 3
        and path[0] == "capabilities"
        and isinstance(path[1], int)
        and path[2] == "CapabilityKey"
    )


def is_reconciliation_historical_identifier_exception(
    artifact: Path,
    path: JsonPath,
    value: str,
) -> bool:
    """Match the exact historical identifier at its two reconciliation paths."""

    return (
        artifact == RECONCILIATION
        and value == RECONCILIATION_HISTORICAL_IDENTIFIER
        and len(path) == 3
        and path[0] in {
            "non_supported_non_deferred_capabilities",
            "capabilities",
        }
        and isinstance(path[1], int)
        and path[2] == "CapabilityKey"
    )


def format_json_path(path: JsonPath) -> str:
    rendered = "$"
    for part in path:
        if isinstance(part, int):
            rendered += f"[{part}]"
        elif JSON_IDENTIFIER_KEY.fullmatch(part):
            rendered += f".{part}"
        else:
            rendered += f"[{json.dumps(part, ensure_ascii=True)}]"
    return rendered


def scan_json_value(
    artifact: Path,
    value: object,
    path: JsonPath = (),
) -> Iterator[str]:
    if isinstance(value, dict):
        for key, child in value.items():
            yield from scan_json_value(artifact, child, path + (key,))
        return

    if isinstance(value, list):
        for index, child in enumerate(value):
            yield from scan_json_value(artifact, child, path + (index,))
        return

    if not isinstance(value, str):
        return

    unresolved = is_unresolved_value(value)
    structured_identifier = is_structured_placeholder_identifier(value)
    if structured_identifier:
        if is_capability_key_exception(artifact, path):
            structured_identifier = False
        elif is_reconciliation_historical_identifier_exception(
            artifact,
            path,
            value,
        ):
            structured_identifier = False

    if unresolved or structured_identifier:
        rendered_value = json.dumps(value, ensure_ascii=True)
        yield (
            f"{artifact}:{format_json_path(path)}: "
            f"unresolved placeholder value {rendered_value}"
        )


def markdown_table_values(line: str) -> tuple[str, ...]:
    stripped = line.strip()
    if not stripped.startswith("|") or not stripped.endswith("|"):
        return ()
    cells = tuple(cell.strip() for cell in stripped[1:-1].split("|"))
    return cells[1:]


def markdown_line_has_unresolved_value(line: str) -> bool:
    table_values = markdown_table_values(line)
    if table_values:
        return any(is_unresolved_value(cell) for cell in table_values)

    assignment = MARKDOWN_ASSIGNMENT.fullmatch(line)
    if assignment is not None and is_unresolved_value(assignment.group(1)):
        return True

    candidate = line.strip()
    while candidate:
        without_prefix = MARKDOWN_PREFIX.sub("", candidate, count=1).strip()
        if without_prefix == candidate:
            break
        candidate = without_prefix

    # A bare asterisk run is Markdown syntax, not a demonstrated required value.
    if MARKDOWN_HORIZONTAL_RULE.fullmatch(candidate):
        return False
    return is_unresolved_value(candidate)


def scan_markdown(artifact: Path, text: str) -> Iterator[str]:
    for line_number, line in enumerate(text.splitlines(), start=1):
        if markdown_line_has_unresolved_value(line):
            yield (
                f"{artifact}:line {line_number}: "
                "unresolved placeholder value"
            )


def read_artifact(root: Path, artifact: Path) -> tuple[str | None, str | None]:
    try:
        return (root / artifact).read_text(encoding="utf-8"), None
    except (OSError, UnicodeError) as error:
        return None, (
            f"{artifact}:<read>: unable to read "
            f"({type(error).__name__})"
        )


def validate(root: Path) -> list[str]:
    findings: list[str] = []
    for artifact in TARGET_ARTIFACTS:
        text, read_finding = read_artifact(root, artifact)
        if read_finding is not None:
            findings.append(read_finding)
            continue
        assert text is not None

        if artifact.suffix == ".json":
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError as error:
                findings.append(
                    f"{artifact}:line {error.lineno}: malformed JSON ({error.msg})"
                )
                continue
            findings.extend(scan_json_value(artifact, parsed))
        else:
            findings.extend(scan_markdown(artifact, text))
    return findings


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate Phase 19 governance artifacts for unresolved values."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="repository root containing the exact target artifacts",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    findings = validate(args.root)
    if findings:
        for finding in findings:
            print(finding)
        print("PLACEHOLDER_GATE=FAIL")
        return 1
    print("PLACEHOLDER_GATE=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
