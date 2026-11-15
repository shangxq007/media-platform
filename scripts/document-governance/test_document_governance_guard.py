#!/usr/bin/env python3
"""Self-test for DG-006 (semantic bodies) and DG-011 (broken links).

Builds an isolated temporary Git repository and proves both guards against a
controlled history: unchanged protected bodies pass, a real protected-document
change fails, an unchanged commit introduces no link, a genuinely new broken
link fails, an unrelated change does not, and invalid refs fail closed.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

GOV_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(GOV_DIR))

from check_markdown_links import check_broken_link_guard  # noqa: E402
from check_semantic_bodies import check_semantic_body_guard, strip_front_matter  # noqa: E402

BASELINE_DIR = "docs/architecture/governance/automated-guards"


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), "-c", "user.name=guard-test", "-c", "user.email=guard-test@example.invalid", *args],
        capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def write(root: Path, relative: str, content: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def commit(root: Path, message: str) -> str:
    git(root, "add", "-A")
    git(root, "commit", "-q", "-m", message)
    return git(root, "rev-parse", "HEAD")


def build_repository(root: Path) -> dict[str, str]:
    git(root, "init", "-q")
    body_v1 = "---\nid: protected\n---\nProtected body version one.\n"
    write(root, "docs/doc.md", body_v1)
    write(root, "docs/target.md", "# target\n")
    write(root, "docs/index.md", "[target](target.md)\n")
    pinned = hashlib.sha256(strip_front_matter(body_v1).encode()).hexdigest()
    write(root, f"{BASELINE_DIR}/protected-document-baseline.json", json.dumps({
        "protected_documents": [{
            "document_id": "test-protected",
            "path": "docs/doc.md",
            "semantic_body_sha256": pinned,
            "baseline_commit": "test",
        }],
        "total": 1,
    }, indent=2) + "\n")
    write(root, f"{BASELINE_DIR}/broken-link-debt-baseline.json", json.dumps({"entries": []}, indent=2) + "\n")
    c1 = commit(root, "base")

    write(root, "docs/good.md", "[target](target.md)\n")
    c2 = commit(root, "adds a valid link")

    write(root, "docs/bad.md", "[missing](missing.md)\n")
    c3 = commit(root, "adds a broken link")

    write(root, "README.md", "unrelated change\n")
    c4 = commit(root, "unrelated change")

    write(root, "docs/doc.md", body_v1.replace("version one", "version two"))
    c5 = commit(root, "changes a protected body")

    return {"c1": c1, "c2": c2, "c3": c3, "c4": c4, "c5": c5}


def main() -> int:
    results: list[tuple[str, bool, str]] = []

    def check(name: str, outcome: dict, expect_pass: bool, expect_code: str | None = None) -> None:
        ok = outcome["passed"] is expect_pass
        if expect_code is not None:
            ok = ok and any(v.get("code") == expect_code for v in outcome["violations"])
        detail = ",".join(sorted({v.get("code", "") for v in outcome["violations"]})) or "no violations"
        results.append((name, ok, detail))

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        commits = build_repository(root)

        check("DG-006 unchanged protected body passes",
              check_semantic_body_guard(commits["c1"], commits["c1"], str(root)), True)
        check("DG-006 unchanged body passes on later commit",
              check_semantic_body_guard(commits["c2"], commits["c2"], str(root)), True)
        check("DG-006 changed protected body fails",
              check_semantic_body_guard(commits["c5"], commits["c5"], str(root)), False, "PROTECTED_BASELINE_CHANGE")
        check("DG-006 invalid head fails closed",
              check_semantic_body_guard("does-not-exist", "does-not-exist", str(root)), False, "INVALID_HEAD_REF")

        check("DG-011 same commit introduces nothing",
              check_broken_link_guard(commits["c1"], commits["c1"], str(root)), True)
        check("DG-011 valid new link passes",
              check_broken_link_guard(commits["c2"], commits["c1"], str(root)), True)
        check("DG-011 genuinely new broken link fails",
              check_broken_link_guard(commits["c3"], commits["c2"], str(root)), False, "INTRODUCED_BROKEN_LINK")
        check("DG-011 same broken commit passes",
              check_broken_link_guard(commits["c3"], commits["c3"], str(root)), True)
        check("DG-011 unrelated change no false positive",
              check_broken_link_guard(commits["c4"], commits["c3"], str(root)), True)
        check("DG-011 invalid ref fails closed",
              check_broken_link_guard("does-not-exist", commits["c3"], str(root)), False, "INVALID_DIFF_INPUT")

    failed = [name for name, ok, _ in results if not ok]
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'}\t{name}\t{detail}")
    if failed:
        print(f"FAIL: {len(failed)} document-governance self-test case(s) failed")
        return 1
    print(f"PASS: {len(results)} document-governance guard self-test cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
