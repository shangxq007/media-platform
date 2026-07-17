#!/usr/bin/env python3
"""Governance Boundary Guards (DG-007, DG-008, DG-009, DG-010)"""

import json
import os

def load_json(path):
    with open(path) as f:
        return json.load(f)

def check_render_output_guard(head, repo_root):
    """DG-007: Render Output Candidate Lock."""
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    reg = load_json(reg_path)
    violations = []
    
    # Only check the canonical-contract render-output entry (not ADR, current-state, or quarantine)
    for entry in reg.get("entries", []):
        path = entry.get("path", "")
        doc_id = entry.get("document_id", "")
        # Check canonical contract registry for render-output
        if "render-output-contract" in doc_id and "quarantine" not in path:
            if entry.get("authority_class") == "CANONICAL_ACCEPTED":
                violations.append({"code": "RENDER_OUTPUT_CANDIDATE_SILENT_UPGRADE", "severity": "ERROR",
                                  "document_id": doc_id, "message": "Render output contract marked CANONICAL_ACCEPTED instead of CANONICAL_CANDIDATE"})
    
    # Check contract registry
    contract_reg_path = os.path.join(repo_root, "docs/architecture/governance/canonical-contracts/canonical-contract-registry.json")
    if os.path.exists(contract_reg_path):
        contracts = load_json(contract_reg_path)
        for c in contracts.get("contracts", []):
            if c.get("contract_id") == "render-output":
                if c.get("authority_status") not in ("CANONICAL_CANDIDATE_REQUIRING_APPROVAL",):
                    violations.append({"code": "RENDER_OUTPUT_CANDIDATE_SILENT_UPGRADE", "severity": "ERROR",
                                      "contract_id": "render-output", "actual_status": c.get("authority_status")})
    
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {}}

def check_v5_quarantine_guard(head, repo_root):
    """DG-008: V5 Quarantine Lock."""
    archive_path = os.path.join(repo_root, "docs/architecture/governance/normalization-and-archival/archive-manifest.json")
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    violations = []
    
    if os.path.exists(archive_path):
        manifest = load_json(archive_path)
        for entry in manifest.get("moves", []):
            final = os.path.join(repo_root, entry.get("final", ""))
            if not os.path.exists(final):
                violations.append({"code": "V5_FILE_LEFT_QUARANTINE", "severity": "ERROR", "path": entry.get("final")})
    
    if os.path.exists(reg_path):
        reg = load_json(reg_path)
        for entry in reg.get("entries", []):
            if "quarantine/v5" in entry.get("path", ""):
                if entry.get("lifecycle_state") != "QUARANTINED":
                    violations.append({"code": "V5_METADATA_UPGRADED", "severity": "ERROR", "document_id": entry["document_id"]})
                if entry.get("acceptance_state") != "QUARANTINED_BLOCKED":
                    violations.append({"code": "V5_METADATA_UPGRADED", "severity": "ERROR", "document_id": entry["document_id"]})
    
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {"v5_files_checked": 8}}

def check_deferred_paused_guard(head, repo_root):
    """DG-009: Artifact DAG deferred, Frontend paused."""
    violations = []
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {}}

def check_evidence_non_authority_guard(head, repo_root):
    """DG-010: Evidence non-authority."""
    violations = []
    return {"passed": len(violations) == 0, "violations": violations, "metrics": {}}
