#!/usr/bin/env python3
"""Fail closed when FAOF-2 proof sources contain proof holes or axioms."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCES = (
    ROOT / "formal/lean/Faof2Graph.lean",
    ROOT / "formal/coq/Faof2Graph.v",
)
FORBIDDEN = re.compile(
    r"\b(?:sorry|admit|Admitted|Axiom|Axioms|axiom|axioms)\b|\bby\?"
)


def without_comments(text: str, block_open: str, block_close: str, line_comment: str | None) -> str:
    output: list[str] = []
    depth = 0
    index = 0
    while index < len(text):
        if line_comment and depth == 0 and text.startswith(line_comment, index):
            newline = text.find("\n", index)
            if newline < 0:
                break
            output.append("\n")
            index = newline + 1
        elif text.startswith(block_open, index):
            depth += 1
            output.append(" ")
            index += len(block_open)
        elif depth and text.startswith(block_close, index):
            depth -= 1
            output.append(" ")
            index += len(block_close)
        else:
            output.append(text[index] if depth == 0 else ("\n" if text[index] == "\n" else " "))
            index += 1
    if depth:
        raise AssertionError("unterminated block comment")
    return "".join(output)


def proof_holes(source: Path, text: str) -> list[str]:
    if source.suffix == ".lean":
        code = without_comments(text, "/-", "-/", "--")
    else:
        code = without_comments(text, "(*", "*)", None)
    failures: list[str] = []
    for match in FORBIDDEN.finditer(code):
        line = code.count("\n", 0, match.start()) + 1
        failures.append(f"{source}:{line}: forbidden {match.group(0)!r}")
    return failures


def main() -> None:
    failures: list[str] = []
    for source in SOURCES:
        if not source.is_file():
            failures.append(f"missing proof source: {source.relative_to(ROOT)}")
            continue
        failures.extend(proof_holes(source.relative_to(ROOT), source.read_text(encoding="utf-8")))
    if failures:
        raise SystemExit("\n".join(failures))
    print(f"FAOF2_PROOF_HOLE_CHECK=PASS sources={len(SOURCES)}")


if __name__ == "__main__":
    main()
