#!/usr/bin/env python3
"""Commit Scope Guard (DG-014)"""

import json
import os
import subprocess

def check_commit_scope_guard(head, base, repo_root, policy=None):
    """DG-014: Commit scope and topology validation."""
    try:
        result = subprocess.run(["git", "rev-list", "--count", f"{base}..{head}"], 
                              capture_output=True, text=True, cwd=repo_root)
        count = int(result.stdout.strip()) if result.returncode == 0 else -1
        
        violations = []
        if policy and policy.get("single_commit") and count != 1:
            violations.append({"code": "MULTIPLE_COMMITS", "severity": "ERROR", "expected": 1, "actual": count})
        
        return {"passed": len(violations) == 0, "violations": violations, "metrics": {"commits": count}}
    except Exception as e:
        return {"passed": False, "violations": [{"code": "INTERNAL_ERROR", "message": str(e)}], "metrics": {}}
