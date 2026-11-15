#!/usr/bin/env python3
"""Broken Link Guard (DG-011).

Compares the broken-link set of the exact ``--head`` commit against the exact
``--base`` commit. Only a link that becomes broken between those two commits and
is not accepted in the debt baseline is a violation, so:

  * ``base == head`` always yields zero introduced links;
  * unrelated changes in other files cannot produce a link violation;
  * a genuinely new broken link fails;
  * legacy debt recorded in broken-link-debt-baseline.json stays tolerated.

Both trees are read from Git objects, never from the working tree, so the result
does not depend on uncommitted or untracked files.
"""

import hashlib
import json
import os
import posixpath
import re
import subprocess

LINK_PATTERN = re.compile(r'\[([^\]]*)\]\(([^)]+)\)')
EXTERNAL_PREFIXES = ('http://', 'https://', 'mailto:', 'ftp:', 'data:', 'javascript:', 'tel:')


def _git(repo_root, *args):
    result = subprocess.run(["git", "-C", repo_root, *args], capture_output=True)
    if result.returncode != 0:
        raise RuntimeError("git " + " ".join(args) + " failed: " +
                           result.stderr.decode("utf-8", "ignore").strip()[:200])
    return result.stdout


def resolve_commit(repo_root, ref):
    return _git(repo_root, "rev-parse", "--verify", "--quiet", ref + "^{commit}").decode().strip()


def tree_paths(repo_root, commit):
    output = _git(repo_root, "ls-tree", "-r", "--name-only", "-z", commit).decode("utf-8", "ignore")
    return {path for path in output.split("\0") if path}


def read_blob(repo_root, commit, path):
    return _git(repo_root, "show", commit + ":" + path).decode("utf-8", "ignore")


def identity_key(source_path, resolved_target):
    return hashlib.sha256(f"{source_path}\n{resolved_target}\nmissing".encode()).hexdigest()[:16]


def scan_links(repo_root, commit, paths):
    """Broken repository-relative links of ``commit``'s docs/**.md files."""
    broken = []
    for path in sorted(p for p in paths if p.startswith("docs/") and p.endswith(".md")):
        content = read_blob(repo_root, commit, path)
        for match in LINK_PATTERN.finditer(content):
            target = match.group(2).strip()
            if any(target.startswith(prefix) for prefix in EXTERNAL_PREFIXES) or target.startswith("#"):
                continue
            path_part = target.split("#")[0].split("?")[0]
            if not path_part:
                continue
            path_part = path_part.replace("%20", " ")
            resolved = posixpath.normpath(posixpath.join(posixpath.dirname(path), path_part))
            if resolved.startswith("../") or resolved in ("..", ".") or posixpath.isabs(resolved):
                exists = False
            else:
                exists = resolved in paths or any(
                    candidate.startswith(resolved.rstrip("/") + "/") for candidate in paths)
            if not exists:
                line = content[:match.start()].count("\n") + 1
                broken.append({
                    "source_path": path,
                    "line": line,
                    "raw_target": target,
                    "resolved_target": resolved,
                    "identity_key": identity_key(path, resolved),
                })
    return broken


def check_broken_link_guard(head, base, repo_root):
    """DG-011: no broken link may be introduced between base and head."""
    baseline_path = os.path.join(repo_root, "docs/architecture/governance/automated-guards/broken-link-debt-baseline.json")

    try:
        head_commit = resolve_commit(repo_root, head)
        base_commit = resolve_commit(repo_root, base)
        head_broken = {link["identity_key"]: link for link in scan_links(repo_root, head_commit, tree_paths(repo_root, head_commit))}
        base_broken = {link["identity_key"] for link in scan_links(repo_root, base_commit, tree_paths(repo_root, base_commit))}
    except RuntimeError as error:
        return {"passed": False, "violations": [{
            "code": "INVALID_DIFF_INPUT", "severity": "ERROR", "head": head, "base": base,
            "message": str(error)}], "metrics": {}}

    if os.path.exists(baseline_path):
        with open(baseline_path) as baseline_file:
            baseline = json.load(baseline_file)
        debt = {entry["identity_key"] for entry in baseline.get("entries", baseline.get("broken_links", []))}
    else:
        debt = set()

    introduced_keys = set(head_broken) - base_broken - debt
    resolved = base_broken - set(head_broken)
    violations = []
    if introduced_keys:
        violations.append({
            "code": "INTRODUCED_BROKEN_LINK",
            "severity": "ERROR",
            "count": len(introduced_keys),
            "message": f"{len(introduced_keys)} new broken link(s) introduced between {base_commit[:12]} and {head_commit[:12]}",
            "links": [head_broken[key] for key in sorted(introduced_keys)],
        })

    return {
        "passed": len(violations) == 0,
        "violations": violations,
        "metrics": {
            "head_commit": head_commit,
            "base_commit": base_commit,
            "head_broken": len(head_broken),
            "base_broken": len(base_broken),
            "accepted_debt": len(debt),
            "introduced": len(introduced_keys),
            "resolved": len(resolved),
        },
    }
