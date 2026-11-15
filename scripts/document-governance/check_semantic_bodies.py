#!/usr/bin/env python3
"""Semantic Body Guard (DG-006).

Protected canonical documents are compared against a pinned semantic body hash.
The body is read from the exact Git commit named by ``--head`` (never from the
possibly dirty working tree), so a run is bound to the commit it claims to check.

A pinned hash may only change with explicit governance evidence recorded in
protected-document-baseline.json (``governance_change_id``, ``semantic_reason``
and ``governance_decision``), mirroring the existing schema-intent precedent.
"""

import hashlib
import json
import os
import subprocess


def strip_front_matter(content):
    """Remove YAML front matter from markdown content."""
    if content.startswith("---"):
        end = content.find("---", 3)
        if end != -1:
            return content[end + 3:].lstrip("\n")
    return content


def load_json(path):
    with open(path) as f:
        return json.load(f)


def _git(repo_root, *args):
    result = subprocess.run(["git", "-C", repo_root, *args], capture_output=True)
    if result.returncode != 0:
        raise RuntimeError("git " + " ".join(args) + " failed: " +
                           result.stderr.decode("utf-8", "ignore").strip()[:200])
    return result.stdout


def resolve_commit(repo_root, ref):
    return _git(repo_root, "rev-parse", "--verify", "--quiet", ref + "^{commit}").decode().strip()


def read_body_at(repo_root, commit, path):
    raw = _git(repo_root, "show", commit + ":" + path)
    return strip_front_matter(raw.decode("utf-8", "ignore"))


def check_semantic_body_guard(head, base, repo_root):
    """DG-006: protected semantic bodies match their pinned governance hash."""
    baseline_path = os.path.join(repo_root, "docs/architecture/governance/automated-guards/protected-document-baseline.json")

    if not os.path.exists(baseline_path):
        return {"passed": True, "violations": [], "metrics": {"note": "No protected baseline present"}}

    try:
        commit = resolve_commit(repo_root, head)
    except RuntimeError as error:
        return {"passed": False, "violations": [{
            "code": "INVALID_HEAD_REF", "severity": "ERROR", "ref": head, "message": str(error)}],
            "metrics": {"checked": 0}}

    baseline = load_json(baseline_path)
    violations = []
    checked = 0

    for entry in baseline.get("protected_documents", []):
        path = entry["path"]
        try:
            body = read_body_at(repo_root, commit, path)
        except RuntimeError:
            # A missing protected document is a real violation, not a skip.
            violations.append({
                "code": "PROTECTED_DOCUMENT_MISSING", "severity": "ERROR",
                "path": path, "document_id": entry.get("document_id", ""), "commit": commit})
            continue
        current_hash = hashlib.sha256(body.encode()).hexdigest()
        checked += 1

        if current_hash != entry.get("semantic_body_sha256", ""):
            violations.append({
                "code": "PROTECTED_BASELINE_CHANGE",
                "severity": "ERROR",
                "path": path,
                "document_id": entry.get("document_id", ""),
                "message": "PROTECTED_BASELINE_CHANGE_REQUIRES_EXPLICIT_GOVERNANCE_APPROVAL",
                "expected": entry.get("semantic_body_sha256", ""),
                "actual": current_hash,
                "commit": commit,
            })

    return {
        "passed": len(violations) == 0,
        "violations": violations,
        "metrics": {"checked": checked, "violations": len(violations), "commit": commit},
    }
