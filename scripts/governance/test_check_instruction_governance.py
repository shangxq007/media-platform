#!/usr/bin/env python3
"""Self-test for check-instruction-governance.py.

Proves the guard accepts the current instruction surfaces and rejects each governance
mutation independently: missing precedence, an adapter without deferral, a stale tool
rule, a contradictory testing rule and a duplicated normative statement.
"""
from __future__ import annotations
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GUARD_REL = "scripts/governance/check-instruction-governance.py"
INSTRUCTION_FILES = ["AGENTS.md", "CLAUDE.md", ".hermes.md", ".kilo/agents/main.md"]


def build(tree: Path) -> None:
    for relative in INSTRUCTION_FILES:
        source = ROOT / relative
        if not source.is_file():
            raise SystemExit(f"missing instruction file: {relative}")
        target = tree / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    guard_target = tree / GUARD_REL
    guard_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / GUARD_REL, guard_target)


def run(tree: Path) -> tuple[int, str]:
    completed = subprocess.run(
        [sys.executable, str(tree / GUARD_REL)], cwd=tree, capture_output=True, text=True)
    return completed.returncode, completed.stdout + completed.stderr


def mutate(tree: Path, relative: str, old: str, new: str) -> None:
    path = tree / relative
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"mutation anchor missing in {relative}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def expect(name: str, mutation, needle: str, results: list[tuple[str, bool, str]]) -> None:
    with tempfile.TemporaryDirectory() as directory:
        tree = Path(directory)
        build(tree)
        mutation(tree)
        code, output = run(tree)
        ok = code != 0 and needle in output
        results.append((name, ok, output.strip().splitlines()[-1] if output.strip() else ""))


def expect_pass(name: str, results: list[tuple[str, bool, str]]) -> None:
    with tempfile.TemporaryDirectory() as directory:
        tree = Path(directory)
        build(tree)
        code, output = run(tree)
        ok = code == 0 and "PASS:" in output
        results.append((name, ok, output.strip().splitlines()[-1] if output.strip() else ""))


def main() -> int:
    results: list[tuple[str, bool, str]] = []
    expect_pass("current instruction set passes", results)
    expect("missing explicit user precedence fails", lambda tree: mutate(
        tree, "AGENTS.md", "Explicit user instructions override repository instructions", "User guidance"),
        "missing explicit user precedence", results)
    def drop_adapter_deferral(tree: Path) -> None:
        path = tree / ".hermes.md"
        path.write_text(path.read_text(encoding="utf-8").replace("AGENTS.md", "the documentation"),
                        encoding="utf-8")

    expect("adapter without AGENTS deferral fails", drop_adapter_deferral,
           "adapter must reference AGENTS.md", results)
    expect("stale tool rule fails", lambda tree: mutate(
        tree, ".hermes.md", "## Working style", "## Working style\n\nDo not push to remote."),
        "push rule that ignores owner authorization", results)
    expect("prohibited database engine fails", lambda tree: mutate(
        tree, ".hermes.md", "H2 is prohibited", "H2 is permitted"), "prohibited database engine", results)
    expect("contradictory testing rule fails", lambda tree: mutate(
        tree, ".hermes.md", "## Working style", "## Working style\n\nTests may be disabled when time is short."),
        "contradictory instruction rules: testing bypass", results)

    def duplicate(tree: Path) -> None:
        nested = tree / "docs/AGENTS.md"
        nested.parent.mkdir(parents=True, exist_ok=True)
        line = next(
            line for line in (tree / "AGENTS.md").read_text(encoding="utf-8").splitlines()
            if len(line.strip()) >= 32 and "does not " not in line.lower()
            and ("must " in line.lower() or "do not " in line.lower()))
        nested.write_text(f"# Nested\n\n{line}\n", encoding="utf-8")

    expect("duplicate normative statement fails", duplicate, "duplicate normative statement", results)

    failed = [name for name, ok, _ in results if not ok]
    for name, ok, last in results:
        print(f"{'PASS' if ok else 'FAIL'}\t{name}\t{last}")
    if failed:
        print(f"FAIL: {len(failed)} guard self-test case(s) failed")
        return 1
    print(f"PASS: {len(results)} instruction-governance guard self-test cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
