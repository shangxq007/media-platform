#!/usr/bin/env python3
"""Fail-closed structural guard for H7 Timeline command authorities.

The guard deliberately ignores Java comments when proving semantics.  It scans
the current production source graph, identifies canonical authority types by
their declarations, and verifies that the H7 and RevisionCommand paths delegate
to those types inside Timeline-owned transactions.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


ZERO_LAWS = (
    "PRODUCT_CURRENT_REVISION_CORRECTNESS_AUTHORITY_COUNT",
    "PRODUCT_LOCAL_MAX_PLUS_ONE_REVISION_ALLOCATION_COUNT",
    "NON_CANONICAL_TIMELINE_HEAD_WRITER_COUNT",
    "DUPLICATE_PROJECT_REVISION_ALLOCATOR_AUTHORITY_COUNT",
    "NORMAL_EDIT_MISSING_PARENT_EDGE_PATH_COUNT",
    "DIRECT_TIMELINE_REF_MUTATION_OUTSIDE_SHARED_AUTHORITY_COUNT",
    "H7_DIRECT_DB_WRITE_OUTSIDE_APPLICATION_TRANSACTION_BOUNDARY_COUNT",
    "AUTHORIZATION_PLAN_BINDING_MISSING_COUNT",
    "DURABLE_IDEMPOTENCY_TRANSACTION_MISSING_COUNT",
    "SHARED_TIMELINE_REF_DELEGATION_MISSING_COUNT",
    "SHARED_PROJECT_REVISION_ALLOCATOR_DELEGATION_MISSING_COUNT",
    "H7_NO_OP_EXPECTED_REF_VALIDATION_MISSING_COUNT",
    "GENESIS_ZERO_PARENT_BOOTSTRAP_SEMANTICS_MISSING_COUNT",
    "CANONICAL_REF_MUTATION_AUTHORITY_MISSING_COUNT",
    "CANONICAL_PROJECT_REVISION_ALLOCATOR_AUTHORITY_MISSING_COUNT",
    "UNCLASSIFIED",
)


@dataclass(frozen=True)
class Evaluation:
    counts: dict[str, int]
    details: tuple[str, ...]

    @property
    def passed(self) -> bool:
        return all(self.counts.get(law, 1) == 0 for law in ZERO_LAWS)


def strip_java_comments(source: str) -> str:
    """Replace comments with whitespace while preserving strings and offsets."""
    out = list(source)
    i = 0
    state = "code"
    quote = ""
    while i < len(source):
        if state == "code":
            if source.startswith("//", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "line"
                continue
            if source.startswith("/*", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "block"
                continue
            if source.startswith('"""', i):
                i += 3
                state = "text"
                continue
            if source[i] in ('"', "'"):
                quote = source[i]
                i += 1
                state = "string"
                continue
            i += 1
            continue
        if state == "line":
            if source[i] == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
            continue
        if state == "block":
            if source.startswith("*/", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if state == "text":
            if source.startswith('"""', i):
                i += 3
                state = "code"
            else:
                i += 1
            continue
        if source[i] == "\\":
            i += 2
        elif source[i] == quote:
            i += 1
            state = "code"
        else:
            i += 1
    return "".join(out)


def mask_java_literals(source: str) -> str:
    """Mask literals without changing offsets, for balanced-brace extraction."""
    out = list(source)
    i = 0
    while i < len(source):
        if source.startswith('"""', i):
            out[i:i + 3] = "   "
            i += 3
            while i < len(source) and not source.startswith('"""', i):
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            if i < len(source):
                out[i:i + 3] = "   "
                i += 3
            continue
        if source[i] not in ('"', "'"):
            i += 1
            continue
        quote = source[i]
        out[i] = " "
        i += 1
        while i < len(source):
            if source[i] == "\\":
                out[i] = " "
                if i + 1 < len(source) and source[i + 1] != "\n":
                    out[i + 1] = " "
                i += 2
            elif source[i] == quote:
                out[i] = " "
                i += 1
                break
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
    return "".join(out)


def extract_braced(source: str, opening: int) -> str | None:
    structural = mask_java_literals(source)
    depth = 0
    for i in range(opening, len(structural)):
        if structural[i] == "{":
            depth += 1
        elif structural[i] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:i]
    return None


def transaction_bodies(source: str) -> list[str]:
    bodies: list[str] = []
    structural = mask_java_literals(source)
    for match in re.finditer(r"\.\s*transactionResult\s*\(", structural):
        opening = structural.find("{", match.end())
        if opening >= 0:
            body = extract_braced(source, opening)
            if body is not None:
                bodies.append(body)
    return bodies


def conditional_bodies(source: str, condition: str) -> list[str]:
    bodies: list[str] = []
    structural = mask_java_literals(source)
    for match in re.finditer(condition, structural):
        opening = structural.find("{", match.end())
        if opening >= 0:
            body = extract_braced(source, opening)
            if body is not None:
                bodies.append(body)
    return bodies


def method_body(source: str, method_name: str) -> str | None:
    structural = mask_java_literals(source)
    pattern = re.compile(
        r"(?:public|protected|private)\s+(?:static\s+)?[\w<>.?]+\s+"
        + re.escape(method_name) + r"\s*\(")
    for match in pattern.finditer(structural):
        opening = structural.find("{", match.end())
        if opening >= 0:
            return extract_braced(source, opening)
    return None


def occurrences(pattern: str, source: str) -> int:
    return len(re.findall(pattern, source, flags=re.IGNORECASE | re.MULTILINE | re.DOTALL))


def production_sources(root: Path) -> dict[str, str]:
    sources: dict[str, str] = {}
    for path in root.rglob("*.java"):
        relative = path.relative_to(root).as_posix()
        qualified = f"/{relative}"
        if ("/src/main/java/" not in qualified or "/build/" in qualified
                or "/generated/" in qualified):
            continue
        source = path.read_text(encoding="utf-8")
        relevant_render_source = (
            relative.startswith("render-module/src/main/java/com/example/platform/render/app/")
            and re.search(
                r"\b(?:OperationPlan|TimelineMediaClipOperation|RevisionWriteCommand|"
                r"saveRevisionForCommand|recordNoOpCommand)\b", source))
        if (relative.startswith("timeline-module/src/main/java/")
                or relevant_render_source
                or relative.endswith("/AuthorizationDecision.java")
                or "timeline_revision_ref" in source.lower()):
            sources[relative] = source
    if not sources:
        raise RuntimeError("no production Java sources found")
    return sources


def class_sources(sources: dict[str, str], simple_name: str) -> list[tuple[str, str]]:
    declaration = re.compile(r"\b(?:public\s+)?(?:final\s+)?class\s+" + re.escape(simple_name) + r"\b")
    return [(path, source) for path, source in sources.items()
            if declaration.search(source)]


def evaluate(raw_sources: dict[str, str]) -> Evaluation:
    details: list[str] = []
    sources = {path: strip_java_comments(source) for path, source in raw_sources.items()}
    generated = {path for path in sources if "/generated/" in f"/{path}"}
    handwritten = {path: source for path, source in sources.items() if path not in generated}

    ref_types = class_sources(sources, "TimelineRevisionRefMutation")
    allocator_types = class_sources(sources, "ProjectRevisionNumberAllocator")
    ref_path = ref_types[0][0] if len(ref_types) == 1 else None
    allocator_path = allocator_types[0][0] if len(allocator_types) == 1 else None

    render_h7 = {
        path: source for path, source in handwritten.items()
        if path.startswith("render-module/src/main/java/com/example/platform/render/app/")
        and re.search(
            r"\b(?:OperationPlan|TimelineMediaClipOperation|RevisionWriteCommand|"
            r"saveRevisionForCommand|recordNoOpCommand)\b", source)
    }
    render_h7_text = "\n".join(render_h7.values())

    timeline_transactions: list[str] = []
    for path, source in handwritten.items():
        if path.startswith("timeline-module/src/main/java/"):
            timeline_transactions.extend(transaction_bodies(source))
    command_write_transactions = [body for body in timeline_transactions
                                  if "claimOrReplayCommand" in body
                                  and "revisionNumberAllocator" in body]
    no_op_transactions = [body for body in timeline_transactions
                          if "claimOrReplayCommand" in body and '"NO_OP"' in body]
    command_branch_bodies: list[str] = []
    for body in command_write_transactions:
        command_branch_bodies.extend(conditional_bodies(
            body, r"\bif\s*\(\s*command\s*!=\s*null\s*\)"))
    h7_correctness_scope = render_h7_text + "\n" + "\n".join(
        command_branch_bodies + no_op_transactions)

    correctness_patterns = (
        r"currentRevisionService\s*\.\s*getCurrentRevisionId\s*\(",
        r"select\s*\(\s*PRODUCT\s*\.\s*CURRENT_REVISION_ID",
        r"select\b[^;]*\bcurrent_revision_id\b",
        r"where\b[^;]*\bcurrent_revision_id\b",
    )
    product_correctness = sum(occurrences(pattern, h7_correctness_scope)
                              for pattern in correctness_patterns)

    max_plus_one_patterns = (
        r"max\s*\(\s*(?:TIMELINE_REVISION\s*\.\s*)?REVISION_NUMBER\s*\)\s*(?:\.\s*add\s*\(\s*1|\+\s*1)",
        r"DSL\s*\.\s*max\s*\([^)]*REVISION_NUMBER[^)]*\)",
        r"max\s*\(\s*revision_number\s*\)\s*\+\s*1",
    )
    revision_command_text = "\n".join(
        source for path, source in handwritten.items()
        if path.startswith("timeline-module/src/main/java/")
        and re.search(r"\bRevisionCommand(?:ApplyService|Plan)\b", source))
    product_command_scope = (render_h7_text + "\n" + revision_command_text
                             + "\n" + "\n".join(command_write_transactions))
    local_max = sum(occurrences(pattern, product_command_scope)
                    for pattern in max_plus_one_patterns)

    head_writer_patterns = (
        r"headUpdatePort\s*\.\s*updateHeadTx\s*\(",
        r"currentRevisionService\s*\.\s*(?:update|compareAndSet|setCurrent)",
        r"\.\s*set\s*\(\s*PRODUCT\s*\.\s*CURRENT_REVISION_ID",
        r"update\s+product\b[^;]*\bcurrent_revision_id\b",
    )
    noncanonical_head_writer = sum(occurrences(pattern, h7_correctness_scope)
                                   for pattern in head_writer_patterns)

    duplicate_allocator = 0
    allocator_authority_pattern = re.compile(
        r"(?:class\s+\w*(?:Project)?Revision(?:Number)?Allocator\b|"
        r"\bPROJECT_REVISION_COUNTER\b.*(?:insertInto|update)\s*\(|"
        r"\b(?:allocate|next)RevisionNumber\s*\()",
        flags=re.IGNORECASE | re.DOTALL)
    allocator_scope = dict(render_h7)
    allocator_scope.update({
        path: source for path, source in handwritten.items()
        if re.search(r"\bRevisionCommand(?:ApplyService|Plan)\b", source)
        or "claimOrReplayCommand" in source
    })
    for path, source in allocator_scope.items():
        if path != allocator_path and allocator_authority_pattern.search(source):
            duplicate_allocator += 1

    ref_mutation_patterns = (
        r"(?:update|insertInto|deleteFrom|mergeInto)\s*\(\s*TIMELINE_REVISION_REF\s*\)",
        r"(?:update|insert\s+into|delete\s+from)\s+timeline_revision_ref\b",
    )
    direct_ref_mutation = 0
    for path, source in handwritten.items():
        if path == ref_path:
            continue
        direct_ref_mutation += sum(occurrences(pattern, source) for pattern in ref_mutation_patterns)

    db_write_patterns = (
        r"\.\s*(?:insertInto|update|deleteFrom|mergeInto)\s*\(",
        r"\.\s*execute\s*\(",
        r"(?:insert\s+into|update|delete\s+from)\s+(?:timeline_|product\b|apply_command\b)",
    )
    h7_direct_db_write = sum(occurrences(pattern, render_h7_text)
                             for pattern in db_write_patterns)

    parent_transaction = any(
        re.search(r"insertInto\s*\(\s*TIMELINE_REVISION_PARENT\s*\)", body)
        and re.search(r"PARENT_REVISION_ID\s*,\s*parentRevisionId", body)
        and re.search(r"PARENT_ORDER\s*,\s*0\s*\)", body)
        and re.search(r"parentRevisionId\s*!=\s*null", body)
        for body in command_write_transactions)

    apply_sources = [source for source in render_h7.values()
                     if method_body(source, "verifyAuthorization") is not None]
    authorization_bound = False
    for source in apply_sources:
        body = method_body(source, "verifyAuthorization") or ""
        call_count = occurrences(r"\bverifyAuthorization\s*\(", source)
        if (call_count >= 2
                and re.search(r"authorization\s*\.\s*planDigest\s*\(\)", body)
                and re.search(r"plan\s*\.\s*planDigest\s*\(\)", body)
                and re.search(r"authorization\s*\.\s*allowed\s*\(\)", body)
                and re.search(r"authorization\s*\.\s*(?:projectId|tenantId|targetRefId|principalRef)\s*\(\)", body)):
            authorization_bound = True
    typed_decision = any(re.search(r"\brecord\s+AuthorizationDecision\s*\(", source)
                         for source in sources.values())
    authorization_bound = authorization_bound and typed_decision

    durable_transaction = any(
        "claimOrReplayCommand" in body
        and "completeCommand" in body
        and "revisionPersistence.insertRevisionTx" in body
        and "revisionRefMutation" in body
        for body in command_write_transactions)

    h7_shared_ref = any("revisionRefMutation" in body for body in command_write_transactions)
    revision_command_shared_ref = bool(re.search(
        r"TimelineRevisionRefMutation|revisionRefMutation\s*\.\s*"
        r"(?:advance|validateExpectedHead|bootstrap|create|delete)", revision_command_text))
    h7_shared_allocator = any("revisionNumberAllocator" in body
                              and re.search(r"\.\s*allocate\s*\(", body)
                              for body in command_write_transactions)
    revision_command_shared_allocator = bool(re.search(
        r"ProjectRevisionNumberAllocator|revisionNumberAllocator\s*\.\s*allocate",
        revision_command_text))

    no_op_expected_validation = any(
        "claimOrReplayCommand" in body
        and re.search(r"revisionRefMutation\s*\.\s*validateExpectedHead\s*\(", body)
        and "completeCommand" in body
        and "revisionPersistence.insertRevisionTx" not in body
        for body in no_op_transactions)

    ref_source = ref_types[0][1] if len(ref_types) == 1 else ""
    allocator_source = allocator_types[0][1] if len(allocator_types) == 1 else ""
    ref_authority_valid = (
        len(ref_types) == 1
        and re.search(r"boolean\s+advance\s*\(", ref_source) is not None
        and re.search(r"boolean\s+validateExpectedHead\s*\(", ref_source) is not None
        and re.search(r"boolean\s+bootstrap\s*\(", ref_source) is not None
    )
    allocator_authority_valid = (
        len(allocator_types) == 1
        and "PROJECT_REVISION_COUNTER" in allocator_source
        and re.search(r"\.\s*onConflict\s*\(", allocator_source) is not None
        and re.search(r"\.\s*doNothing\s*\(", allocator_source) is not None
        and re.search(r"update\s*\(\s*PROJECT_REVISION_COUNTER\s*\)", allocator_source) is not None
        and re.search(r"\.\s*returning\s*\(", allocator_source) is not None
    )
    genesis_semantics = (
        ref_authority_valid
        and re.search(r"HEAD_REVISION_ID\s*\.\s*isNull\s*\(", ref_source) is not None
        and re.search(r"VERSION\s*,\s*0L", ref_source) is not None
        and any(re.search(r"parentRevisionId\s*==\s*null", body)
                and re.search(r"revisionRefMutation\s*\.\s*bootstrap\s*\(", body)
                and re.search(r"revisionRefMutation\s*\.\s*advance\s*\(", body)
                for body in command_write_transactions)
        and parent_transaction
    )

    counts = {
        "PRODUCT_CURRENT_REVISION_CORRECTNESS_AUTHORITY_COUNT": product_correctness,
        "PRODUCT_LOCAL_MAX_PLUS_ONE_REVISION_ALLOCATION_COUNT": local_max,
        "NON_CANONICAL_TIMELINE_HEAD_WRITER_COUNT": noncanonical_head_writer,
        "DUPLICATE_PROJECT_REVISION_ALLOCATOR_AUTHORITY_COUNT": duplicate_allocator,
        "NORMAL_EDIT_MISSING_PARENT_EDGE_PATH_COUNT": 0 if parent_transaction else 1,
        "DIRECT_TIMELINE_REF_MUTATION_OUTSIDE_SHARED_AUTHORITY_COUNT": direct_ref_mutation,
        "H7_DIRECT_DB_WRITE_OUTSIDE_APPLICATION_TRANSACTION_BOUNDARY_COUNT": h7_direct_db_write,
        "AUTHORIZATION_PLAN_BINDING_MISSING_COUNT": 0 if authorization_bound else 1,
        "DURABLE_IDEMPOTENCY_TRANSACTION_MISSING_COUNT": 0 if durable_transaction else 1,
        "SHARED_TIMELINE_REF_DELEGATION_MISSING_COUNT":
            0 if h7_shared_ref and revision_command_shared_ref else 1,
        "SHARED_PROJECT_REVISION_ALLOCATOR_DELEGATION_MISSING_COUNT":
            0 if h7_shared_allocator and revision_command_shared_allocator else 1,
        "H7_NO_OP_EXPECTED_REF_VALIDATION_MISSING_COUNT":
            0 if no_op_expected_validation else 1,
        "GENESIS_ZERO_PARENT_BOOTSTRAP_SEMANTICS_MISSING_COUNT":
            0 if genesis_semantics else 1,
        "CANONICAL_REF_MUTATION_AUTHORITY_MISSING_COUNT":
            0 if ref_authority_valid else 1,
        "CANONICAL_PROJECT_REVISION_ALLOCATOR_AUTHORITY_MISSING_COUNT":
            0 if allocator_authority_valid else 1,
        "UNCLASSIFIED": 0,
        "CANONICAL_TIMELINE_REF_MUTATION_AUTHORITY_TYPE_COUNT": len(ref_types),
        "CANONICAL_PROJECT_REVISION_ALLOCATOR_TYPE_COUNT": len(allocator_types),
    }
    if len(ref_types) != 1:
        details.append(f"expected one TimelineRevisionRefMutation type, found {len(ref_types)}")
    if len(allocator_types) != 1:
        details.append(f"expected one ProjectRevisionNumberAllocator type, found {len(allocator_types)}")
    for law in ZERO_LAWS:
        if counts[law] != 0:
            details.append(f"{law}={counts[law]}")
    return Evaluation(counts, tuple(details))


def inject_before_last_brace(source: str, addition: str) -> str:
    at = source.rfind("}")
    if at < 0:
        return source + addition
    return source[:at] + addition + "\n" + source[at:]


def mutate_source(sources: dict[str, str], class_name: str,
                  transform: Callable[[str], str]) -> dict[str, str]:
    mutated = dict(sources)
    matches = class_sources(mutated, class_name)
    if len(matches) != 1:
        raise RuntimeError(f"self-test cannot uniquely locate {class_name}")
    path, source = matches[0]
    mutated[path] = transform(source)
    return mutated


def run_self_test(sources: dict[str, str]) -> bool:
    cases: list[tuple[str, str, dict[str, str], bool]] = []

    cases.append(("product_pointer_correctness_decision",
                  "PRODUCT_CURRENT_REVISION_CORRECTNESS_AUTHORITY_COUNT",
                  mutate_source(sources, "OperationPlanApplyService", lambda source:
                      inject_before_last_brace(source,
                          "void badCurrentDecision(DSLContext tx) { "
                          "tx.select(PRODUCT.CURRENT_REVISION_ID).fetchOne(); }")), False))
    cases.append(("max_plus_one_allocator",
                  "PRODUCT_LOCAL_MAX_PLUS_ONE_REVISION_ALLOCATION_COUNT",
                  mutate_source(sources, "OperationPlanApplyService", lambda source:
                      inject_before_last_brace(source,
                          "long badNumber() { return DSL.max(TIMELINE_REVISION.REVISION_NUMBER)"
                          ".add(1).hashCode(); }")), False))
    cases.append(("direct_ref_mutation",
                  "DIRECT_TIMELINE_REF_MUTATION_OUTSIDE_SHARED_AUTHORITY_COUNT",
                  mutate_source(sources, "OperationPlanApplyService", lambda source:
                      inject_before_last_brace(source,
                          "void badRef(DSLContext tx) { tx.update(TIMELINE_REVISION_REF)"
                          ".set(TIMELINE_REVISION_REF.HEAD_REVISION_ID, \"r2\").execute(); }")), False))

    def without_parent(source: str) -> str:
        return re.sub(
            r"\s*if\s*\(parentRevisionId\s*!=\s*null\)\s*\{.*?"
            r"\.set\(TIMELINE_REVISION_PARENT\.PARENT_ORDER,\s*0\)\s*"
            r"\.execute\(\);\s*\}", "", source, count=1, flags=re.DOTALL)

    cases.append(("missing_normal_parent_edge",
                  "NORMAL_EDIT_MISSING_PARENT_EDGE_PATH_COUNT",
                  mutate_source(sources, "TimelineRevisionSaveService", without_parent), False))
    cases.append(("duplicate_h7_allocator",
                  "DUPLICATE_PROJECT_REVISION_ALLOCATOR_AUTHORITY_COUNT",
                  mutate_source(sources, "OperationPlanApplyService", lambda source:
                      inject_before_last_brace(source,
                          "static final class H7LocalRevisionAllocator { long allocate(DSLContext tx) { "
                          "return tx.update(PROJECT_REVISION_COUNTER).execute(); } }")), False))
    cases.append(("direct_product_pointer_publication",
                  "NON_CANONICAL_TIMELINE_HEAD_WRITER_COUNT",
                  mutate_source(sources, "OperationPlanApplyService", lambda source:
                      inject_before_last_brace(source,
                          "void badPublish(DSLContext tx) { headUpdatePort.updateHeadTx("
                          "tx, projectId, expectedHead, revisionId); }")), False))

    comments_only = mutate_source(
        mutate_source(sources, "TimelineRevisionSaveService", without_parent),
        "OperationPlanApplyService", lambda source: inject_before_last_brace(source,
            "// expected-head CAS base parent parent_order = 0\n"
            "// timeline_revision_parent expected CAS semantics are present"))
    cases.append(("marker_comments_cannot_supply_semantics",
                  "NORMAL_EDIT_MISSING_PARENT_EDGE_PATH_COUNT", comments_only, False))

    relocated = dict(sources)
    apply_match = class_sources(relocated, "OperationPlanApplyService")
    if len(apply_match) != 1:
        raise RuntimeError("self-test cannot relocate OperationPlanApplyService")
    old_path, relocated_source = apply_match[0]
    del relocated[old_path]
    relocated["render-module/src/main/java/com/example/platform/render/app/operation/"
              "RelocatedOperationCoordinator.java"] = relocated_source
    save_match = class_sources(relocated, "TimelineRevisionSaveService")
    if len(save_match) != 1:
        raise RuntimeError("self-test cannot relocate TimelineRevisionSaveService")
    old_save_path, relocated_save_source = save_match[0]
    del relocated[old_save_path]
    relocated["timeline-module/src/main/java/com/example/platform/timeline/app/internal/"
              "RelocatedTimelineRevisionSaveService.java"] = relocated_save_source.replace(
                  "saveRevisionInternal", "persistCommandRevision")
    cases.append(("shared_authority_delegation_survives_source_relocation",
                  "ALL_ZERO_LAWS", relocated, True))

    cases.append(("missing_authorization_plan_binding",
                  "AUTHORIZATION_PLAN_BINDING_MISSING_COUNT",
                  mutate_source(sources, "OperationPlanApplyService", lambda source:
                      source.replace("authorization.planDigest()", "plan.planDigest()", 1)), False))
    cases.append(("missing_durable_command_claim",
                  "DURABLE_IDEMPOTENCY_TRANSACTION_MISSING_COUNT",
                  mutate_source(sources, "TimelineRevisionSaveService", lambda source:
                      source.replace("claimOrReplayCommand", "omittedCommandClaim")), False))
    cases.append(("missing_no_op_expected_ref_validation",
                  "H7_NO_OP_EXPECTED_REF_VALIDATION_MISSING_COUNT",
                  mutate_source(sources, "TimelineRevisionSaveService", lambda source:
                      source.replace("revisionRefMutation.validateExpectedHead(",
                                     "revisionRefMutation.currentHead(", 1)), False))
    cases.append(("missing_genesis_bootstrap",
                  "GENESIS_ZERO_PARENT_BOOTSTRAP_SEMANTICS_MISSING_COUNT",
                  mutate_source(sources, "TimelineRevisionSaveService", lambda source:
                      source.replace("revisionRefMutation.bootstrap(",
                                     "revisionRefMutation.advance(", 1)), False))

    failures = 0
    for name, target, mutated, should_pass in cases:
        result = evaluate(mutated)
        accepted = result.passed if should_pass else result.counts.get(target, 0) > 0
        if not accepted:
            failures += 1
        observed = "PASS" if accepted else "FAIL"
        detected = "ALL_ZERO_LAWS" if should_pass else f"{target}={result.counts.get(target, 0)}"
        print(f"MUTATION {name}={observed} {detected}")
    print(f"MUTATION_MATRIX_TOTAL={len(cases)}")
    print(f"MUTATION_MATRIX_FAILURES={failures}")
    return failures == 0


def repository_root(start: Path) -> Path:
    current = start.resolve()
    for candidate in (current, *current.parents):
        if (candidate / "settings.gradle.kts").is_file():
            return candidate
    raise RuntimeError(f"repository root not found from {start}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        root = repository_root(args.root)
        sources = production_sources(root)
        baseline = evaluate(sources)
    except (OSError, RuntimeError) as failure:
        print(f"UNCLASSIFIED=1", file=sys.stderr)
        print(f"H7_GUARD_ERROR={failure}", file=sys.stderr)
        return 2

    for law in ZERO_LAWS:
        print(f"{law}={baseline.counts[law]}")
    print("CANONICAL_TIMELINE_REF_MUTATION_AUTHORITY_TYPE_COUNT="
          f"{baseline.counts['CANONICAL_TIMELINE_REF_MUTATION_AUTHORITY_TYPE_COUNT']}")
    print("CANONICAL_PROJECT_REVISION_ALLOCATOR_TYPE_COUNT="
          f"{baseline.counts['CANONICAL_PROJECT_REVISION_ALLOCATOR_TYPE_COUNT']}")
    for detail in baseline.details:
        print(f"H7_GUARD_DETAIL={detail}", file=sys.stderr)
    if not baseline.passed:
        return 1
    if args.self_test and not run_self_test(sources):
        return 1
    print("H7_ARCHITECTURE_GUARD=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
