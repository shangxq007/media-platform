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

ZERO_FIELDS = (
    "RENDER_TO_NOTIFICATION_PRODUCTION_DEPENDENCY_COUNT",
    "RENDER_NOVU_REFERENCE_COUNT",
    "IDENTITY_NOVU_REFERENCE_COUNT",
    "PROJECT_ID_AS_SUBSCRIBER_COUNT",
    "TENANT_ID_AS_SUBSCRIBER_COUNT",
    "ARBITRARY_TENANT_USER_FALLBACK_COUNT",
    "DUPLICATE_PRINCIPAL_ID_AUTHORITY_COUNT",
    "SYSTEM_RENDER_FAKE_PRINCIPAL_COUNT",
    "CURRENT_AMBIENT_ACTOR_AT_COMPLETION_COUNT",
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
    for relative in (
        "render-module/src/main",
        "render-module/build.gradle.kts",
        "identity-access-module/src/main",
        "identity-access-module/build.gradle.kts",
        "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
        "shared-kernel/src/main/java/com/example/platform/shared/events",
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


def collect(root: Path) -> tuple[dict[str, int], dict[str, list[tuple[Path, int]]], int]:
    files = scan_files(root)
    render = source_files(files, root, "render-module/")
    identity = source_files(files, root, "identity-access-module/")
    production_java = [
        path for path in files
        if "/src/main/java/" in f"/{path.relative_to(root).as_posix()}"
    ]

    details: dict[str, list[tuple[Path, int]]] = {}
    details[ZERO_FIELDS[0]] = findings_for(
        render,
        r"com\.example\.platform\.notification|NotificationEventPublisher|project\(\s*[\"']?:notification-module|:notification-module",
    )
    details[ZERO_FIELDS[1]] = findings_for(render, r"\bnovu")
    details[ZERO_FIELDS[2]] = findings_for(identity, r"\bnovu")
    details[ZERO_FIELDS[3]] = findings_for(
        production_java,
        r"(?:subscriber|recipient|audience)[A-Za-z0-9_]*\s*\([^\n)]*\bprojectId\b|\bprojectId\b[^\n]*(?:subscriber|recipient|audience)",
    )
    details[ZERO_FIELDS[4]] = findings_for(
        production_java,
        r"(?:subscriber|recipient|audience)[A-Za-z0-9_]*\s*\([^\n)]*\btenantId\b|\btenantId\b[^\n]*(?:subscriber|recipient|audience)",
    )
    details[ZERO_FIELDS[5]] = findings_for(
        production_java,
        r"(?:find|select|load|get)[A-Za-z0-9_]*(?:Tenant)?(?:User|Member)[A-Za-z0-9_]*(?:First|Any)|(?:tenant|member)[^\n]{0,80}findFirst\s*\(",
    )
    details[ZERO_FIELDS[6]] = findings_for(
        production_java,
        r"\b(?:record|class|interface)\s+(?:PrincipalId|ActorId)\b",
    )
    details[ZERO_FIELDS[7]] = findings_for(
        render,
        r"CanonicalActor\.user\s*\(|RenderInitiator\.principal\s*\([^\n]*(?:projectId|tenantId|@example\.)|new\s+Principal\s*\([^\n]*(?:projectId|tenantId|@example\.)",
    )

    completion_files = []
    for path in render:
        text = read(path)
        if re.search(r"new\s+RenderJob(?:Completed|Failed)Event\s*\(", text) and re.search(
            r"CanonicalActorResolver|SecurityContextHolder|resolveCurrentActor\s*\(", text
        ):
            completion_files.append((path, 0))
    details[ZERO_FIELDS[8]] = completion_files

    schema_path = root / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
    schema = render_job_schema_block(read(schema_path)) if schema_path.is_file() else ""
    schema_count = len(re.findall(
        r"(?im)^\s*initiator_(?:type|id|tenant_id)\s+varchar\s*\(", schema
    ))

    counts = {name: len(details[name]) for name in ZERO_FIELDS}
    counts.update({
        "RENDER_INITIATOR_SCHEMA_COLUMN_COUNT": schema_count,
        "RENDER_COMPLETED_EVENT_INITIATOR_FIELD_COUNT": event_field_count(root, "RenderJobCompletedEvent"),
        "RENDER_FAILED_EVENT_INITIATOR_FIELD_COUNT": event_field_count(root, "RenderJobFailedEvent"),
        "UNCLASSIFIED": 0,
    })
    return counts, details, len(files)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    counts, details, universe = collect(root)

    print(f"SCAN_UNIVERSE_FILE_COUNT={universe}")
    for name in (*ZERO_FIELDS, *EXPECTED_FIELDS):
        print(f"{name}={counts[name]}")
        for path, line in details.get(name, []):
            relative = path.relative_to(root).as_posix()
            print(f"  HIT {relative}:{line}")

    failed = universe == 0
    if universe == 0:
        print("ERROR=EMPTY_SCAN_UNIVERSE")
    failed |= any(counts[name] != 0 for name in ZERO_FIELDS)
    failed |= any(counts[name] != expected for name, expected in EXPECTED_FIELDS.items())
    print(f"H10_R1_RENDER_INITIATOR_GUARD={'FAIL' if failed else 'PASS'}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
