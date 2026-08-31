#!/usr/bin/env python3
"""Mechanical V2 convergence guard for the H7 canonical Timeline runtime."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def without_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", " ", text)


def java_sources() -> dict[str, str]:
    result: dict[str, str] = {}
    for path in ROOT.rglob("src/main/java/**/*.java"):
        relative = path.relative_to(ROOT).as_posix()
        if "/build/" not in f"/{relative}":
            result[relative] = without_comments(path.read_text(encoding="utf-8"))
    return result


def count(pattern: str, text: str) -> int:
    return len(re.findall(pattern, text, flags=re.IGNORECASE | re.MULTILINE | re.DOTALL))


def main() -> int:
    sources = java_sources()
    production = "\n".join(sources.values())
    schema = without_comments((ROOT / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql")
                              .read_text(encoding="utf-8"))

    revision_writer_occurrences = 0
    for path, source in sources.items():
        if path.endswith("DefaultTimelineRevisionPersistence.java"):
            continue
        revision_writer_occurrences += count(
            r"insertInto\s*\(\s*TIMELINE_REVISION\s*\)|insert\s+into\s+timeline_revision\b",
            source)

    mutation_threadlocal = 0
    for suffix in (
        "TimelineRevisionSaveService.java",
        "TimelinePatchApplicationService.java",
        "TimelineMergeEngine.java",
        "RevisionCommandApplyService.java",
    ):
        mutation_threadlocal += sum(
            count(r"\b(?:TenantContext|TenantGuard)\s*\.\s*(?:get|requireTenantId)\s*\(", source)
            for path, source in sources.items() if path.endswith(suffix))

    internal_persistence = 0
    for source in sources.values():
        internal_persistence += count(
            r"(?:saveTx|save|insertRevisionTx|insertTx)\s*\([^;]*?['\"]internal-1\.0['\"]|"
            r"insert\s+into\s+timeline_(?:snapshot|revision)[^;]*?internal-1\.0",
            source)

    request_author = sum(count(pattern, production) for pattern in (
        r"saveService\s*\.\s*saveRevision\s*\([^;]*?request\s*\.\s*createdBy\s*\(",
        r"saveService\s*\.\s*restoreRevision\s*\([^;]*?request\s*\.\s*createdBy\s*\(",
        r"revisionSaveService\s*\.\s*restoreRevision\s*\([^;]*?authorUserId",
        r"new\s+TimelineRevisionRepository\.RevisionRow\s*\([^;]*?request\s*\.\s*authorUserId\s*\(",
    ))
    timeline_controller_source = "\n".join(
        source for path, source in sources.items()
        if "/web/render/Timeline" in path and path.endswith("Controller.java")
    )
    request_author += count(r"body\s*\.\s*authorUserId\s*\(", timeline_controller_source)
    request_author += count(r"@RequestParam\s+String\s+reviewerUserId", timeline_controller_source)

    save_service_path = next((p for p in sources if p.endswith("TimelineRevisionSaveService.java")), "")
    snapshot_writer_callers = sum(
        count(r"(?:timelineSnapshotService|snapshotService)\s*\.\s*(?:save|saveTx)\s*\(", source)
        for path, source in sources.items() if path != save_service_path
    )

    controller_auth_missing = 0
    for controller in (
        "TimelineGitV1Controller.java",
        "TimelineRevisionController.java",
        "TimelineSnapshotController.java",
        "TimelineEditorSyncController.java",
        "TimelineWorkbenchController.java",
        "TimelineReviewController.java",
    ):
        source = next((s for p, s in sources.items() if p.endswith(controller)), "")
        if "projectAuthorization.require" not in source:
            controller_auth_missing += 1
    media_controller = next((s for p, s in sources.items()
                             if p.endswith("TimelineMediaClipOperationController.java")), "")
    media_service = next((s for p, s in sources.items()
                          if p.endswith("TimelineMediaClipOperationService.java")), "")
    if "authenticatedActor" not in media_controller or "requirePreparationAuthorization" not in media_service:
        controller_auth_missing += 1
    mcp_media_controller = next((s for p, s in sources.items()
                                 if p.endswith("McpMediaToolsController.java")), "")
    if "projectAuthorization.requireWrite" not in mcp_media_controller:
        controller_auth_missing += 1
    media_asset_lifecycle = next((s for p, s in sources.items()
                                  if p.endswith("MediaAssetLifecycleController.java")), "")
    if ("projectAuthorization.requireRead" not in media_asset_lifecycle
            or "projectAuthorization.requireWrite" not in media_asset_lifecycle):
        controller_auth_missing += 1

    generated = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated").rglob("*.java")
    )

    save_source = next((s for p, s in sources.items()
                        if p.endswith("TimelineRevisionSaveService.java")), "")
    merge_source = next((s for p, s in sources.items()
                         if p.endswith("TimelineMergeEngine.java")), "")
    restore_parent_missing = 0 if (
        "restoreRevision" in save_source
        and "PARENT_REVISION_ID, expectedCurrentRevisionId" in save_source
        and "PARENT_ORDER, 0" in save_source) else 1
    merge_parent_missing = 0 if (
        "saveMergeRevision" in merge_source
        and "additionalParents" in save_source
        and "parentOrder + 1" in save_source) else 1

    laws = {
        "PRODUCT_CURRENT_REVISION_ID_CANONICAL_READERS": count(
            r"PRODUCT\s*\.\s*CURRENT_REVISION_ID|product\.current_revision_id|\bcurrent_revision_id\b",
            production + "\n" + schema),
        "MAX_REVISION_NUMBER_HEAD_INFERENCE": count(
            r"max\s*\(\s*revision_number\s*\)|REVISION_NUMBER\s*\.\s*max\s*\(", production),
        "CURRENT_HEAD_BY_MAX_REVISION_NUMBER_CALLERS": count(
            r"find(?:Owned)?Head\s*\([^)]*\)\s*\{[^}]*orderBy\s*\([^)]*REVISION_NUMBER\s*\.\s*desc",
            production),
        "DIRECT_REVISION_WRITERS_OUTSIDE_CANONICAL_BOUNDARY": revision_writer_occurrences,
        "CANONICAL_REVISION_MUTATION_BOUNDARIES": count(
            r"class\s+TimelineRevisionSaveService\b", production),
        "REQUEST_CONTROLLED_CANONICAL_AUTHOR": request_author,
        "COMMAND_PATH_THREADLOCAL_TENANT_AUTHORITY": mutation_threadlocal,
        "MAX_PLUS_ONE_REVISION_ALLOCATORS": count(
            r"nextRevisionNumberTx\s*\(|max\s*==\s*null\s*\?\s*1\s*:\s*max\s*\+\s*1",
            production),
        "INTERNAL_1_0_CANONICAL_PERSISTENCE_WRITERS": internal_persistence,
        "STANDALONE_SNAPSHOT_WRITER_CALLERS": snapshot_writer_callers,
        "TIMELINE_DOCUMENT_READERS_OUTSIDE_PRODUCTION_CODEC": sum(
            count(r"(?:readValue|readerFor|treeToValue)\s*\([^;]*TimelineDocument", source)
            for path, source in sources.items()
            if not path.endswith("TimelineDocumentJsonSerializer.java")),
        "CONTENT_HASH_MIXED_SEMANTIC_WRITERS": count(
            r"TIMELINE_REVISION\s*\.\s*CONTENT_HASH\s*,?\s*revision\s*\.\s*contentDigest\s*\(",
            production),
        "ACTIVE_TIMELINE_CONTROLLER_AUTHORIZATION_MISSING": controller_auth_missing,
        "MERGE_CONTENT_HASH_NONCANONICAL_SEMANTICS": count(
            r"computeMergeHash\s*\(|merge:\s*['\"]?\s*\+\s*source", merge_source),
        "SHADOW_INTERNAL_PATCH_REPLAY_SURFACES": count(
            r"previewPatchReplay|previewPatchSteps|hashInternalTimeline",
            next((s for p, s in sources.items()
                  if p.endswith("TimelineRevisionDiffQuery.java")), "")),
        "REVISION_COMPARE_SHADOW_SEMANTIC_DIFF_AUTHORITY": count(
            r"TimelineSemanticDiffService|InternalTimelineJson",
            next((s for p, s in sources.items()
                  if p.endswith("TimelineRevisionDiffQuery.java")), "")),
        "SHADOW_SIMPLIFIED_CANONICAL_SCHEMA_FIXTURES": 0 if
            "CANONICAL_POSTGRES_COVERAGE = false" in
            (ROOT / "render-module/src/test/java/com/example/platform/render/testsupport/RenderTestSchemaFixture.java")
            .read_text(encoding="utf-8") else 1,
        "RESTORE_MISSING_ORDERED_PARENT_EDGES": restore_parent_missing,
        "MERGE_MISSING_TWO_ORDERED_PARENT_EDGES": merge_parent_missing,
        "REVISION_COMMAND_RUNTIME_PATHS": count(
            r"class\s+(?:RevisionCommandApplyService|RevisionCommandPlanner)\b", production),
        "SNAPSHOT_TENANT_NULLABLE": count(
            r"create\s+table\s+timeline_snapshot\s*\([^;]*?tenant_id\s+varchar\(64\)\s*(?:,|\n)", schema),
        "REVISION_TENANT_NULLABLE": count(
            r"create\s+table\s+timeline_revision\s*\([^;]*?tenant_id\s+varchar\(64\)\s*(?:,|\n)", schema),
        "SNAPSHOT_COMPOSITE_OWNERSHIP_FK_MISSING": 0 if re.search(
            r"foreign\s+key\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*snapshot_id\s*\)\s*"
            r"references\s+timeline_snapshot\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*id\s*\)", schema,
            flags=re.IGNORECASE | re.DOTALL) else 1,
        "REF_COMPOSITE_OWNERSHIP_FK_MISSING": 0 if re.search(
            r"foreign\s+key\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*head_revision_id\s*\)\s*"
            r"references\s+timeline_revision\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*id\s*\)", schema,
            flags=re.IGNORECASE | re.DOTALL) else 1,
        "PARENT_COMPOSITE_OWNERSHIP_FKS_MISSING": 0 if (
            count(r"foreign\s+key\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*revision_id\s*\)", schema) >= 1
            and count(r"foreign\s+key\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*parent_revision_id\s*\)", schema) >= 1
            and count(r"references\s+timeline_revision\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*id\s*\)", schema) >= 4
        ) else 1,
        "ARTIFACT_PIN_COMPOSITE_OWNERSHIP_FKS_MISSING": 0 if (
            re.search(r"foreign\s+key\s*\(\s*tenant_id\s*,\s*artifact_id\s*\).*?"
                      r"references\s+artifact\s*\(\s*tenant_id\s*,\s*id\s*\)", schema,
                      flags=re.IGNORECASE | re.DOTALL)
            and re.search(r"foreign\s+key\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*revision_id\s*\).*?"
                          r"references\s+timeline_revision\s*\(\s*tenant_id\s*,\s*project_id\s*,\s*id\s*\)", schema,
                          flags=re.IGNORECASE | re.DOTALL)
        ) else 1,
        "GENERATED_JOOQ_V1_PARITY_MARKERS_MISSING": 0 if all(marker in generated for marker in (
            "EXPECTED_HEAD_REVISION_ID", "EXPECTED_RESULT_STATUS", "TARGET_REF_ID",
            "UQ_TIMELINE_REVISION_OWNER_ID", "UQ_TIMELINE_SNAPSHOT_OWNER_ID",
            "TIMELINE_REVISION_PARENT.PARENT_ORDER", "ARTIFACT_PIN.TENANT_ID",
        )) else 1,
    }

    failures = 0
    for name, value in laws.items():
        print(f"{name}={value}")
        expected = 1 if name == "CANONICAL_REVISION_MUTATION_BOUNDARIES" else 0
        if value != expected:
            failures += 1
    print(f"H7_V2_ARCHITECTURE_FAILURES={failures}")
    print("H7_V2_ARCHITECTURE_GUARD=" + ("PASS" if failures == 0 else "FAIL"))
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
