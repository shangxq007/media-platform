#!/usr/bin/env python3
"""Semantic Body Guard (DG-006)"""

import hashlib
import json
import os
import re

def strip_front_matter(content):
    """Remove YAML front matter from markdown content."""
    if content.startswith("---"):
        end = content.find("---", 3)
        if end != -1:
            return content[end+3:].lstrip("\n")
    return content

def load_json(path):
    with open(path) as f:
        return json.load(f)

def check_semantic_body_guard(head, base, repo_root):
    """DG-006: Protected semantic bodies unchanged."""
    baseline_path = os.path.join(repo_root, "docs/architecture/governance/automated-guards/protected-document-baseline.json")
    reg_path = os.path.join(repo_root, "docs/architecture/governance/metadata-and-lifecycle/document-metadata-registry.json")
    
    if not os.path.exists(baseline_path):
        return {"passed": True, "violations": [], "metrics": {"note": "No baseline yet — first run"}, "baseline_exists": False}
    
    baseline = load_json(baseline_path)
    violations = []
    checked = 0
    
    for entry in baseline.get("protected_documents", []):
        path = os.path.join(repo_root, entry["path"])
        if not os.path.exists(path):
            continue
        with open(path, 'r', errors='ignore') as f:
            content = f.read()
        body = strip_front_matter(content)
        current_hash = hashlib.sha256(body.encode()).hexdigest()
        checked += 1
        
        if current_hash != entry.get("semantic_body_sha256", ""):
            violations.append({
                "code": "PROTECTED_BASELINE_CHANGE",
                "severity": "ERROR",
                "path": entry["path"],
                "document_id": entry.get("document_id", ""),
                "message": "PROTECTED_BASELINE_CHANGE_REQUIRES_EXPLICIT_GOVERNANCE_APPROVAL",
                "expected": entry.get("semantic_body_sha256", ""),
                "actual": current_hash
            })
    
    return {
        "passed": len(violations) == 0,
        "violations": violations,
        "metrics": {"checked": checked, "violations": len(violations)}
    }
