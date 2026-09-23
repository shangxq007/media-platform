#!/usr/bin/env python3
"""Fail-closed repository instruction inventory and conflict guard."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
files = sorted(p for p in ROOT.rglob("*") if p.is_file() and p.name in {"AGENTS.md", "CLAUDE.md"} and ".git" not in p.parts)
errors = []
if not files:
    errors.append("no instruction files found")
root_agents = ROOT / "AGENTS.md"
root_claude = ROOT / "CLAUDE.md"
if root_agents not in files:
    errors.append("missing root AGENTS.md")
if root_claude not in files:
    errors.append("missing root CLAUDE.md")

for path in files:
    text = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT)
    scope = path.parent.relative_to(ROOT) or Path(".")
    print(f"{rel}\tscope={scope}\taudience={'repository-agents' if path.name == 'AGENTS.md' else 'claude-tool-adapter'}")
    if path.name == "AGENTS.md":
        if "canonical repository instruction authority" not in text:
            errors.append(f"{rel}: missing canonical-authority declaration")
        if "Explicit user instructions override repository instructions" not in text:
            errors.append(f"{rel}: missing explicit user precedence")
        if "stop implementation, record a governance finding" not in text:
            errors.append(f"{rel}: missing unresolved-conflict stop rule")
        if "nested `AGENTS.md`" not in text:
            errors.append(f"{rel}: missing nested scope rule")
    else:
        if path == root_claude:
            if "canonical repository instructions are in" not in text:
                errors.append("CLAUDE.md: missing AGENTS reference")
            forbidden = ["Do not introduce:", "Frozen Boundaries", "public APIs", "must not expose provider IDs"]
            for token in forbidden:
                if token in text:
                    errors.append(f"CLAUDE.md: contains independent normative token {token!r}")
        elif "tool-specific adapter" not in text:
            errors.append(f"{rel}: nested CLAUDE.md must identify itself as a tool adapter")

root_text = root_agents.read_text(encoding="utf-8") if root_agents.exists() else ""
# These are intentionally exact, high-risk governance concepts. They must have one
# normative occurrence in root AGENTS, while adapters may mention only delegation.
for label, patterns in {
    "public API exposure": [r"provider IDs, manifests, ExecutionBackend", r"provider/backend/environment/storage-provider identity in public APIs"],
    "provider authority": [r"Capability Registry is the authority", r"provider resolution"],
    "testing/delivery": [r"Do not fetch, pull, push, publish, deploy", r"Do not weaken, disable, bypass"],
}.items():
    count = sum(len(re.findall(pattern, root_text, flags=re.I)) for pattern in patterns)
    if count < 1:
        errors.append(f"root AGENTS.md: missing normative {label} rule")

# Duplicate normative statements are forbidden across AGENTS files. A nested
# file may add unique scope detail, but it cannot copy root safety/authority text.
agent_files = [p for p in files if p.name == "AGENTS.md"]
statements = {}
for path in agent_files:
    for line in path.read_text(encoding="utf-8").splitlines():
        normalized = re.sub(r"\s+", " ", line.strip().lower())
        if len(normalized) >= 32 and ("must " in normalized or "do not " in normalized or "may not " in normalized):
            statements.setdefault(normalized, []).append(str(path.relative_to(ROOT)))
for statement, owners in statements.items():
    if len(owners) > 1:
        errors.append(f"duplicate normative statement across AGENTS.md files: {statement!r} ({owners})")

# High-risk contradiction scan across all adapters/instruction files.
all_text = "\n".join(p.read_text(encoding="utf-8") for p in files)
contradictions = [
    (r"(?:may|can) expose provider (?:ids|identit)", r"must not expose provider (?:ids|identit)", "public provider identity exposure"),
    (r"provider(?:s)? (?:define|own|are the authority for) platform", r"provider-owned platform contracts", "provider authority"),
    (r"(?:may|can) disable tests|tests may be disabled", r"do not (?:weaken, )?disable", "testing bypass"),
    (r"(?:may|can) push|push is allowed", r"do not .*push", "delivery push"),
    (r"(?:may|can) deploy|deploy is allowed", r"do not .*deploy", "delivery deploy"),
]
for allow_pattern, deny_pattern, label in contradictions:
    if re.search(allow_pattern, all_text, re.I) and re.search(deny_pattern, all_text, re.I):
        errors.append(f"contradictory instruction rules: {label}")
if re.search(r"may expose provider (?:ids|identit)", all_text, re.I):
    errors.append("provider/backend identity is authorized by an ambiguous public rule")
if re.search(r"provider.*identit(?:y|ies).*public API", all_text, re.I) and not re.search(r"remain internal unless", root_text, re.I):
    errors.append("provider/public API rule lacks internal-only exception")
if "higher-scope" not in root_text.lower() or "more-specific nested" not in root_text.lower():
    errors.append("precedence declaration is incomplete")

if errors:
    print("FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print(f"PASS: {len(files)} instruction files inventoried; no governance conflicts detected")
