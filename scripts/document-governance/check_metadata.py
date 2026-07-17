#!/usr/bin/env python3
"""Metadata and Registry Guard (DG-003, DG-004)"""

import json
import os

def load_json(path):
    with open(path) as f:
        return json.load(f)

def check_metadata_guard(head, repo_root):
    """DG-003: Registry coverage and identity."""
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    disp_path = os.path.join(repo_root, "docs/architecture/governance/normalization-and-archival/document-disposition-register.json")
    
    if not os.path.exists(reg_path):
        return {"passed": False, "violations": [{"code": "REGISTRY_MISSING", "severity": "ERROR", "path": reg_path}], "metrics": {}}
    
    reg = load_json(reg_path)
    disp = load_json(disp_path) if os.path.exists(disp_path) else {"total_documents": 0, "dispositions": []}
    
    violations = []
    entries = reg.get("entries", [])
    
    # Check counts
    if len(entries) != disp.get("total_documents", 0):
        violations.append({"code": "REGISTRY_COVERAGE_MISMATCH", "severity": "ERROR", 
                          "expected": disp.get("total_documents"), "actual": len(entries)})
    
    # Check unique IDs
    ids = [e.get("document_id", "") for e in entries]
    dup_ids = [x for x in set(ids) if ids.count(x) > 1]
    if dup_ids:
        violations.append({"code": "DUPLICATE_DOCUMENT_ID", "severity": "ERROR", "count": len(dup_ids)})
    
    # Check unique paths
    paths = [e.get("path", "") for e in entries]
    dup_paths = [x for x in set(paths) if paths.count(x) > 1]
    if dup_paths:
        violations.append({"code": "DUPLICATE_PATH", "severity": "ERROR", "count": len(dup_paths)})
    
    # Check paths exist
    missing_paths = [p for p in paths if p and not os.path.exists(os.path.join(repo_root, p))]
    
    return {
        "passed": len(violations) == 0,
        "violations": violations,
        "metrics": {"entries": len(entries), "duplicate_ids": len(dup_ids), "duplicate_paths": len(dup_paths), "missing_paths": len(missing_paths)}
    }

def check_tier_metadata_guard(head, repo_root):
    """DG-004: Tier metadata integrity."""
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    reg = load_json(reg_path)
    entries = reg.get("entries", [])
    
    tier1 = [e for e in entries if e.get("metadata_tier") == "TIER_1"]
    tier2 = [e for e in entries if e.get("metadata_tier") == "TIER_2"]
    
    violations = []
    
    # Check Tier 1 inline metadata
    tier1_missing = 0
    for entry in tier1:
        path = os.path.join(repo_root, entry["path"])
        if os.path.exists(path) and path.endswith(".md"):
            with open(path, 'r', errors='ignore') as f:
                first = f.readline().strip()
            if first != "---":
                tier1_missing += 1
    
    if tier1_missing > 0:
        violations.append({"code": "TIER_1_INLINE_METADATA_MISSING", "severity": "ERROR", "count": tier1_missing})
    
    return {
        "passed": len(violations) == 0,
        "violations": violations,
        "metrics": {"tier_1": len(tier1), "tier_2": len(tier2), "tier_1_missing_inline": tier1_missing}
    }
