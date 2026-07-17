#!/usr/bin/env python3
"""Document Governance Guard — Main Entry Point (6R)"""

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))

EXIT_ALL_PASS = 0
EXIT_VIOLATION = 1
EXIT_INVALID_USAGE = 2
EXIT_INPUT_ERROR = 3
EXIT_CONFIG_ERROR = 4
EXIT_INTERNAL_ERROR = 5

def run_guard(guard_id, func, *args, **kwargs):
    """Run a single guard and capture result."""
    start = datetime.now(timezone.utc)
    try:
        result = func(*args, **kwargs)
        end = datetime.now(timezone.utc)
        return {
            "guard_id": guard_id,
            "status": "PASS" if result.get("passed", True) else "FAIL",
            "executed": True,
            "violations": result.get("violations", []),
            "metrics": result.get("metrics", {}),
            "duration_ms": int((end - start).total_seconds() * 1000)
        }
    except Exception as e:
        end = datetime.now(timezone.utc)
        return {
            "guard_id": guard_id,
            "status": "ERROR",
            "executed": True,
            "violations": [{"code": "INTERNAL_ERROR", "severity": "ERROR", "message": str(e)}],
            "metrics": {},
            "duration_ms": int((end - start).total_seconds() * 1000)
        }

def check_required_artifacts(head, repo_root):
    """DG-001: Required governance artifacts exist."""
    required = [
        "docs/architecture/governance/inventory/ARCH-DOC-GOV-INVENTORY-AND-CLASSIFICATION.1.md",
        "docs/architecture/governance/source-of-truth/source-of-truth-matrix.json",
        "docs/architecture/governance/canonical-contracts/canonical-contract-registry.json",
        "docs/architecture/governance/normalization-and-archival/document-disposition-register.json",
        "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json",
        "docs/architecture/governance/automated-guards/document-governance-guard-catalog.json",
    ]
    violations = []
    for path in required:
        if not os.path.exists(os.path.join(repo_root, path)):
            violations.append({"code": "REQUIRED_ARTIFACT_MISSING", "severity": "ERROR", "path": path})
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {"checked": len(required)}}

def check_json_schema_validity(head, repo_root):
    """DG-002: JSON and Schema Validity."""
    import json as json_mod
    json_dirs = [
        "docs/architecture/governance/inventory",
        "docs/architecture/governance/source-of-truth",
        "docs/architecture/governance/canonical-contracts",
        "docs/architecture/governance/normalization-and-archival",
        "docs/architecture/governance/metadata-and-lifecycle",
        "docs/architecture/governance/automated-guards",
    ]
    violations = []
    checked = 0
    for d in json_dirs:
        full_d = os.path.join(repo_root, d)
        if not os.path.exists(full_d):
            continue
        for f in os.listdir(full_d):
            if f.endswith('.json'):
                path = os.path.join(full_d, f)
                try:
                    with open(path) as fh:
                        json_mod.load(fh)
                    checked += 1
                except Exception as e:
                    violations.append({"code": "JSON_PARSE_FAILURE", "severity": "ERROR", "path": path, "message": str(e)})
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {"json_checked": checked}}

def check_ownership_lifecycle(head, repo_root):
    """DG-005: Ownership, Lifecycle and Relationships."""
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    if not os.path.exists(reg_path):
        return {"passed": False, "violations": [{"code": "REGISTRY_MISSING", "severity": "ERROR"}], "metrics": {}}
    reg = json.load(open(reg_path))
    violations = []
    valid_authority = {"CANONICAL_ACCEPTED","CANONICAL_CANDIDATE","NORMATIVE_SUPPORTING","EXECUTABLE_SOURCE","SCHEMA_SOURCE","INFORMATIVE","GENERATED","EVIDENCE_ONLY","FORENSIC_ONLY","HISTORICAL","QUARANTINED","REJECTED","AUTHORITY_UNRESOLVED"}
    valid_lifecycle = {"ACTIVE","FROZEN","CANDIDATE","SUPERSEDED","DEPRECATED","ARCHIVED","HISTORICAL","QUARANTINED","REJECTED","MISSING","UNVERIFIED","UNKNOWN"}
    valid_acceptance = {"ACCEPTED","NOT_ACCEPTED","PARTIALLY_ACCEPTED","BLOCKED","QUARANTINED_BLOCKED","NOT_APPLICABLE","UNKNOWN"}
    for e in reg.get("entries", []):
        if e.get("authority_class") not in valid_authority:
            violations.append({"code": "UNKNOWN_AUTHORITY_CLASS", "severity": "ERROR", "document_id": e.get("document_id"), "actual": e.get("authority_class")})
        if e.get("lifecycle_state") not in valid_lifecycle:
            violations.append({"code": "INVALID_LIFECYCLE_STATE", "severity": "ERROR", "document_id": e.get("document_id"), "actual": e.get("lifecycle_state")})
        if e.get("acceptance_state") not in valid_acceptance:
            violations.append({"code": "INVALID_ACCEPTANCE_STATE", "severity": "ERROR", "document_id": e.get("document_id"), "actual": e.get("acceptance_state")})
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {"entries_checked": len(reg.get("entries", []))}}

def check_generated_document(head, repo_root):
    """DG-015: Generated-Document Lifecycle Guard."""
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    if not os.path.exists(reg_path):
        return {"passed": True, "violations": [], "metrics": {}}
    reg = json.load(open(reg_path))
    violations = []
    for e in reg.get("entries", []):
        if e.get("generated") and e.get("authority_class") in ("CANONICAL_ACCEPTED", "CANONICAL_CANDIDATE"):
            violations.append({"code": "GENERATED_PROMOTED_TO_CANONICAL", "severity": "ERROR", "document_id": e.get("document_id")})
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {}}

def main():
    parser = argparse.ArgumentParser(description="Document Governance Guard")
    parser.add_argument("--head", required=True, help="Git ref to check")
    parser.add_argument("--base", help="Base git ref for transition mode")
    parser.add_argument("--mode", default="current", choices=["current", "transition"])
    parser.add_argument("--output", default="/tmp/document-governance-report.json")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    started_at = datetime.now(timezone.utc).isoformat()
    results = []

    # Import guard modules
    sys.path.insert(0, SCRIPT_DIR)
    from check_metadata import check_metadata_guard, check_tier_metadata_guard
    from check_semantic_bodies import check_semantic_body_guard
    from check_markdown_links import check_broken_link_guard
    from check_governance_boundaries import (
        check_render_output_guard, check_v5_quarantine_guard,
        check_deferred_paused_guard, check_evidence_non_authority_guard
    )
    from validate_receipts import check_receipt_schema_guard, check_receipt_ordering_guard
    from check_commit_scope import check_commit_scope_guard

    # All 16 guards - each independently executed
    guards = [
        ("DG-001", lambda: check_required_artifacts(args.head, REPO_ROOT)),
        ("DG-002", lambda: check_json_schema_validity(args.head, REPO_ROOT)),
        ("DG-003", lambda: check_metadata_guard(args.head, REPO_ROOT)),
        ("DG-004", lambda: check_tier_metadata_guard(args.head, REPO_ROOT)),
        ("DG-005", lambda: check_ownership_lifecycle(args.head, REPO_ROOT)),
        ("DG-006", lambda: check_semantic_body_guard(args.head, args.base or args.head, REPO_ROOT)),
        ("DG-007", lambda: check_render_output_guard(args.head, REPO_ROOT)),
        ("DG-008", lambda: check_v5_quarantine_guard(args.head, REPO_ROOT)),
        ("DG-009", lambda: check_deferred_paused_guard(args.head, REPO_ROOT)),
        ("DG-010", lambda: check_evidence_non_authority_guard(args.head, REPO_ROOT)),
        ("DG-011", lambda: check_broken_link_guard(args.head, args.base or args.head, REPO_ROOT)),
        ("DG-012", lambda: check_receipt_schema_guard(args.head, REPO_ROOT)),
        ("DG-013", lambda: check_receipt_ordering_guard(args.head, REPO_ROOT)),
        ("DG-014", lambda: check_commit_scope_guard(args.head, args.base or args.head, REPO_ROOT, {"single_commit": args.mode == "transition"})),
        ("DG-015", lambda: check_generated_document(args.head, REPO_ROOT)),
        ("DG-016", lambda: _check_architecture_guard(REPO_ROOT)),
    ]

    for guard_id, func in guards:
        results.append(run_guard(guard_id, func))

    # Summary
    completed_at = datetime.now(timezone.utc).isoformat()
    executed = sum(1 for r in results if r.get("executed"))
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] in ("FAIL", "ERROR"))

    report = {
        "tool": "document-governance-guard",
        "schema_version": 1,
        "base": args.base or "",
        "head": args.head,
        "mode": args.mode,
        "started_at": started_at,
        "completed_at": completed_at,
        "guards_expected": 16,
        "guards_executed": executed,
        "guards_passed": passed,
        "guards_failed": failed,
        "guards_skipped": 0,
        "results": sorted(results, key=lambda r: r["guard_id"]),
        "decision": "PASS" if failed == 0 else "FAIL"
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, 'w') as f:
        json.dump(report, f, indent=2, sort_keys=False)

    if args.verbose:
        print(json.dumps(report, indent=2))

    exit_code = EXIT_ALL_PASS if failed == 0 else EXIT_VIOLATION
    print(f"Guards: {passed}/{executed} passed, decision: {report['decision']}")
    return exit_code

def _check_architecture_guard(repo_root):
    """DG-016: Existing architecture guard integration."""
    try:
        result = subprocess.run(
            ["bash", os.path.join(repo_root, "scripts", "check-architecture-drift.sh")],
            capture_output=True, text=True, cwd=repo_root
        )
        return {
            "passed": result.returncode == 0,
            "violations": [] if result.returncode == 0 else [{"code": "ARCHITECTURE_GUARD_FAILED", "severity": "ERROR", "message": result.stdout[-500:]}],
            "metrics": {"exit_code": result.returncode, "checks": 32}
        }
    except Exception as e:
        return {"passed": False, "violations": [{"code": "INTERNAL_ERROR", "message": str(e)}], "metrics": {}}

if __name__ == "__main__":
    sys.exit(main())
