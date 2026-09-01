#!/usr/bin/env python3
"""H10-R1 guard for immutable Render request-initiator provenance.

The guard scans repository-owned source only.  Findings are deterministic
``(category, path, line)`` tuples and generated/build/worktree content is never
part of the scan universe.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


EXCLUDED_PARTS = {".git", ".worktrees", "build", "generated", "node_modules"}

PRODUCTION_SCOPE = (
    "render-module/src/main",
    "delivery-module/src/main",
    "outbox-event-module/src/main",
    "platform-app/src/main",
    "identity-access-module/src/main",
    "shared-kernel/src/main",
    "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
)

ZERO_FIELDS = (
    "DELIVERY_RENDER_INITIATOR_RAW_TABLE_READ_COUNT",
    "FINALIZE_FAILED_DELIVERY_AUTO_RETRY_COUNT",
    "FINALIZE_RENDER_JOB_RAW_READ_COUNT",
    "FINALIZE_INITIATOR_RECONSTRUCTION_COUNT",
    "CURRENT_AMBIENT_ACTOR_AT_COMPLETION_COUNT",
    "CURRENT_AMBIENT_ACTOR_AT_FAILURE_COUNT",
    "RENDER_TO_NOTIFICATION_PRODUCTION_DEPENDENCY_COUNT",
    "DUPLICATE_PRINCIPAL_ID_AUTHORITY_COUNT",
    "SYSTEM_RENDER_FAKE_PRINCIPAL_COUNT",
    "PROJECT_ID_AS_NOTIFICATION_AUDIENCE_COUNT",
    "TENANT_ID_AS_NOTIFICATION_AUDIENCE_COUNT",
    "ARBITRARY_TENANT_USER_FALLBACK_COUNT",
    "NEW_SCHEMA_CHANGE_BEYOND_EXISTING_H10_R1_INITIATOR_COLUMNS",
    "RENDER_NOVU_REFERENCE_COUNT",
    "IDENTITY_NOVU_REFERENCE_COUNT",
    "MISSING_INITIATOR_AT_SUBMISSION_COUNT",
)

EXPECTED_FIELDS = {
    "RENDER_INITIATOR_SCHEMA_COLUMN_COUNT": 3,
    "RENDER_COMPLETED_EVENT_INITIATOR_FIELD_COUNT": 1,
    "RENDER_FAILED_EVENT_INITIATOR_FIELD_COUNT": 1,
    "UNCLASSIFIED": 0,
}


def is_excluded(path: Path, root: Path) -> bool:
    try:
        relative = path.relative_to(root)
    except ValueError:
        return True
    return any(part in EXCLUDED_PARTS for part in relative.parts)


def scan_files(root: Path) -> list[Path]:
    candidates: set[Path] = set()
    for relative in (*PRODUCTION_SCOPE,
        "render-module/build.gradle.kts",
        "identity-access-module/build.gradle.kts",
    ):
        path = root / relative
        if path.is_file() and not is_excluded(path, root):
            candidates.add(path)
        elif path.is_dir():
            candidates.update(
                item for item in path.rglob("*")
                if item.is_file() and not is_excluded(item, root)
            )
    return sorted(candidates, key=lambda path: path.relative_to(root).as_posix())


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def matching_lines(path: Path, expression: str, flags: int = re.IGNORECASE) -> list[int]:
    pattern = re.compile(expression, flags)
    return [number for number, line in enumerate(read(path).splitlines(), start=1) if pattern.search(line)]


def source_files(files: list[Path], root: Path, prefix: str) -> list[Path]:
    return [
        path for path in files
        if path.relative_to(root).as_posix().startswith(prefix)
    ]


def findings_for(files: list[Path], expression: str) -> list[tuple[Path, int]]:
    return [(path, line) for path in files for line in matching_lines(path, expression)]


def mask_java_comments_and_literals(text: str) -> str:
    """Mask Java comments and literals while preserving offsets and newlines."""
    masked = list(text)
    index = 0
    while index < len(text):
        delimiter = None
        if text.startswith("//", index):
            end = text.find("\n", index + 2)
            end = len(text) if end == -1 else end
        elif text.startswith("/*", index):
            closing = text.find("*/", index + 2)
            end = len(text) if closing == -1 else closing + 2
        elif text.startswith('\"\"\"', index):
            delimiter = '\"\"\"'
            closing = text.find(delimiter, index + len(delimiter))
            end = len(text) if closing == -1 else closing + len(delimiter)
        elif text[index] in {'\"', "'"}:
            delimiter = text[index]
            end = index + 1
            while end < len(text):
                if text[end] == "\\":
                    end += 2
                    continue
                end += 1
                if text[end - 1] == delimiter:
                    break
        else:
            index += 1
            continue
        for masked_index in range(index, min(end, len(text))):
            if masked[masked_index] != "\n":
                masked[masked_index] = " "
        index = end
    return "".join(masked)


def matching_delimiter(text: str, opening: int, open_char: str, close_char: str) -> int | None:
    depth = 0
    for index in range(opening, len(text)):
        if text[index] == open_char:
            depth += 1
        elif text[index] == close_char:
            depth -= 1
            if depth == 0:
                return index
    return None


def java_method_bodies(text: str, method_name: str) -> list[tuple[str, int]]:
    """Return method bodies and source offsets using balanced Java delimiters."""
    masked = mask_java_comments_and_literals(text)
    bodies = []
    for match in re.finditer(rf"\b{re.escape(method_name)}\s*\(", masked):
        opening_parenthesis = masked.find("(", match.start())
        closing_parenthesis = matching_delimiter(masked, opening_parenthesis, "(", ")")
        if closing_parenthesis is None:
            continue
        declaration_end = re.search(r"[;{}]", masked[closing_parenthesis + 1:])
        if declaration_end is None:
            continue
        opening_brace = closing_parenthesis + 1 + declaration_end.start()
        if masked[opening_brace] != "{":
            continue
        closing_brace = matching_delimiter(masked, opening_brace, "{", "}")
        if closing_brace is not None:
            bodies.append((text[opening_brace + 1:closing_brace], opening_brace + 1))
    return bodies


def findings_in_regions(
    path: Path,
    text: str,
    regions: list[tuple[str, int]],
    expression: str,
) -> list[tuple[Path, int]]:
    pattern = re.compile(expression, re.IGNORECASE)
    return [
        (path, text.count("\n", 0, offset + match.start()) + 1)
        for region, offset in regions
        for match in pattern.finditer(region)
    ]


def render_job_schema_block(text: str) -> str:
    match = re.search(
        r"(?is)\bcreate\s+table\s+render_job\s*\((.*?)\)\s*;",
        text,
    )
    return match.group(1) if match else ""


def event_field_count(root: Path, event_name: str) -> int:
    path = root / (
        "shared-kernel/src/main/java/com/example/platform/shared/events/"
        f"{event_name}.java"
    )
    if not path.is_file():
        return 0
    text = read(path)
    header = re.search(rf"(?s)record\s+{re.escape(event_name)}\s*\((.*?)\)\s*\{{", text)
    return len(re.findall(r"\bRenderInitiator\s+initiator\b", header.group(1) if header else ""))


def collect(root: Path) -> tuple[dict[str, int], dict[str, list[tuple[Path, int]]], int, bool]:
    files = scan_files(root)
    render = source_files(files, root, "render-module/")
    delivery = source_files(files, root, "delivery-module/src/main/")
    identity = source_files(files, root, "identity-access-module/")
    production_java = [
        path for path in files
        if "/src/main/java/" in f"/{path.relative_to(root).as_posix()}"
    ]

    details: dict[str, list[tuple[Path, int]]] = {}
    details["DELIVERY_RENDER_INITIATOR_RAW_TABLE_READ_COUNT"] = findings_for(
        delivery,
        r"\bRENDER_JOB\s*\.\s*INITIATOR_(?:TYPE|ID|TENANT_ID)\b",
    )
    delivery_service = root / (
        "delivery-module/src/main/java/com/example/platform/delivery/app/DeliveryJobService.java"
    )
    delivery_service_text = read(delivery_service) if delivery_service.is_file() else ""
    finalize_bodies = java_method_bodies(
        delivery_service_text,
        "finalizeDeliveriesForRenderJob",
    )
    details["FINALIZE_FAILED_DELIVERY_AUTO_RETRY_COUNT"] = findings_in_regions(
        delivery_service,
        delivery_service_text,
        finalize_bodies,
        r"\bDeliveryJobStatus\s*\.\s*FAILED\b|"
        r"\bDELIVERY_JOB\s*\.\s*STATUS\b[^;\n]{0,160}(?:==|\.eq\s*\()\s*[\"']FAILED[\"']",
    )
    details["FINALIZE_RENDER_JOB_RAW_READ_COUNT"] = findings_in_regions(
        delivery_service,
        delivery_service_text,
        finalize_bodies,
        r"\bRENDER_JOB\b",
    )
    details["FINALIZE_INITIATOR_RECONSTRUCTION_COUNT"] = findings_in_regions(
        delivery_service,
        delivery_service_text,
        finalize_bodies,
        r"\bRenderInitiator\s*\.\s*(?:restore|principal)\s*\(|"
        r"\bnew\s+RenderInitiator\b|\bCanonicalActor\b",
    )

    ambient_expression = r"CanonicalActorResolver|SecurityContextHolder|resolveCurrentActor\s*\("
    completion_files = []
    failure_files = []
    for path in production_java:
        text = read(path)
        if re.search(r"new\s+RenderJobCompletedEvent\s*\(", text) and re.search(ambient_expression, text):
            completion_files.append((path, 0))
        if re.search(r"new\s+RenderJobFailedEvent\s*\(", text) and re.search(ambient_expression, text):
            failure_files.append((path, 0))
    details["CURRENT_AMBIENT_ACTOR_AT_COMPLETION_COUNT"] = completion_files
    details["CURRENT_AMBIENT_ACTOR_AT_FAILURE_COUNT"] = failure_files

    details["RENDER_TO_NOTIFICATION_PRODUCTION_DEPENDENCY_COUNT"] = findings_for(
        render,
        r"com\.example\.platform\.notification|NotificationEventPublisher|project\(\s*[\"']?:notification-module|:notification-module",
    )
    details["DUPLICATE_PRINCIPAL_ID_AUTHORITY_COUNT"] = findings_for(
        production_java,
        r"\b(?:record|class|interface)\s+(?:PrincipalId|ActorId)\b",
    )
    details["SYSTEM_RENDER_FAKE_PRINCIPAL_COUNT"] = findings_for(
        render,
        r"CanonicalActor\.user\s*\(|RenderInitiator\.restore\s*\(\s*ActorType\.SYSTEM|RenderInitiator\.principal\s*\([^\n]*(?:projectId|tenantId|@example\.)|new\s+Principal\s*\([^\n]*(?:projectId|tenantId|@example\.)",
    )
    details["PROJECT_ID_AS_NOTIFICATION_AUDIENCE_COUNT"] = findings_for(
        production_java,
        r"(?=.*\b(?:notification|novu)[A-Za-z0-9_]*)(?=.*(?:subscriber|recipient|audience))(?=.*\bprojectId\b)",
    )
    details["TENANT_ID_AS_NOTIFICATION_AUDIENCE_COUNT"] = findings_for(
        production_java,
        r"(?=.*\b(?:notification|novu)[A-Za-z0-9_]*)(?=.*(?:subscriber|recipient|audience))(?=.*\btenantId\b)",
    )
    details["ARBITRARY_TENANT_USER_FALLBACK_COUNT"] = findings_for(
        production_java,
        r"(?:find|select|load|get)[A-Za-z0-9_]*(?:Tenant)?(?:User|Member)[A-Za-z0-9_]*(?:First|Any)|(?:tenant|member)[^\n]{0,80}findFirst\s*\(",
    )
    details["RENDER_NOVU_REFERENCE_COUNT"] = findings_for(render, r"\bnovu")
    details["IDENTITY_NOVU_REFERENCE_COUNT"] = findings_for(identity, r"\bnovu")
    details["MISSING_INITIATOR_AT_SUBMISSION_COUNT"] = findings_for(
        production_java,
        r"(?:submitRenderJob|submissionService\s*\.\s*submit)\s*\([^;\n]*,\s*null\s*\)",
    )

    schema_path = root / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
    schema = render_job_schema_block(read(schema_path)) if schema_path.is_file() else ""
    schema_count = len(re.findall(
        r"(?im)^\s*initiator_(?:type|id|tenant_id)\s+varchar\s*\(", schema
    ))
    allowed_schema_fields = {"type", "id", "tenant_id"}
    schema_change_lines = []
    if schema_path.is_file():
        for line_number, line in enumerate(schema.splitlines(), start=1):
            match = re.search(r"(?i)^\s*initiator_([a-z0-9_]+)\b", line)
            if match and match.group(1).lower() not in allowed_schema_fields:
                schema_change_lines.append((schema_path, line_number))
    details["NEW_SCHEMA_CHANGE_BEYOND_EXISTING_H10_R1_INITIATOR_COLUMNS"] = schema_change_lines

    counts = {name: len(details[name]) for name in ZERO_FIELDS}
    counts.update({
        "RENDER_INITIATOR_SCHEMA_COLUMN_COUNT": schema_count,
        "RENDER_COMPLETED_EVENT_INITIATOR_FIELD_COUNT": event_field_count(root, "RenderJobCompletedEvent"),
        "RENDER_FAILED_EVENT_INITIATOR_FIELD_COUNT": event_field_count(root, "RenderJobFailedEvent"),
        "UNCLASSIFIED": 0,
    })
    scope_complete = all(
        any(
            path == root / relative
            or path.relative_to(root).as_posix().startswith(relative.rstrip("/") + "/")
            for path in files
        )
        for relative in PRODUCTION_SCOPE
    )
    return counts, details, len(files), scope_complete


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    counts, details, universe, scope_complete = collect(root)

    print(f"SCAN_UNIVERSE_FILE_COUNT={universe}")
    for name in (*ZERO_FIELDS, *EXPECTED_FIELDS):
        print(f"{name}={counts[name]}")
        for path, line in details.get(name, []):
            relative = path.relative_to(root).as_posix()
            print(f"  HIT {relative}:{line}")

    scope_status = (
        "COMPLETE_FOR_H10_R1_CHANGED_SURFACES"
        if scope_complete else "INCOMPLETE_FOR_H10_R1_CHANGED_SURFACES"
    )
    print(f"H10_R1_GUARD_PRODUCTION_SCOPE={scope_status}")

    failed = universe == 0 or not scope_complete
    if universe == 0:
        print("ERROR=EMPTY_SCAN_UNIVERSE")
    failed |= any(counts[name] != 0 for name in ZERO_FIELDS)
    failed |= any(counts[name] != expected for name, expected in EXPECTED_FIELDS.items())
    print(f"H10_R1_RENDER_INITIATOR_GUARD={'FAIL' if failed else 'PASS'}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
