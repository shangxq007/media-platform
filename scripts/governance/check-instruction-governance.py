#!/usr/bin/env python3
"""Fail-closed repository instruction inventory and conflict guard.

Covers every instruction surface a coding agent can auto-load:
  AGENTS.md (root and nested), CLAUDE.md (root and nested), .hermes.md and .kilo/agents/*.md

The guard prints the complete inventory (path, scope, audience) and fails when precedence
declarations are missing, an adapter redefines architecture authority or carries stale tool facts,
normative statements are duplicated across AGENTS files, or contradictory rules coexist. It
inspects instruction text only; it never executes product runtime behaviour.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SKIP_PARTS = {".git", "build", "bin", "node_modules", ".gradle", "dist"}
errors: list[str] = []


def load(relative: str) -> Path | None:
    path = ROOT / relative
    return path if path.is_file() else None


def discover(name: str) -> list[Path]:
    return sorted(p for p in ROOT.rglob(name)
                  if p.is_file() and not any(part in SKIP_PARTS for part in p.parts))


def flat(text: str) -> str:
    return re.sub(r"\s+", " ", text)


root_agents = load("AGENTS.md")
root_claude = load("CLAUDE.md")
tool_adapters = [p for p in [load(".hermes.md")] if p]
if (ROOT / ".kilo" / "agents").is_dir():
    tool_adapters += sorted((ROOT / ".kilo" / "agents").glob("*.md"))
claude_adapters = [p for p in discover("CLAUDE.md") if p != root_claude]
nested_agents = [p for p in discover("AGENTS.md") if p != root_agents]

if root_agents is None:
    errors.append("missing root AGENTS.md")
if root_claude is None:
    errors.append("missing root CLAUDE.md")
if not tool_adapters:
    errors.append("no tool adapter instruction files found")

inventory: list[tuple[Path, str]] = []
if root_agents:
    inventory.append((root_agents, "repository-agents"))
if root_claude:
    inventory.append((root_claude, "claude-tool-adapter"))
inventory += [(path, "nested-repository-agents") for path in nested_agents]
inventory += [(path, "claude-tool-adapter") for path in claude_adapters]
inventory += [(path, "tool-adapter") for path in tool_adapters]

for path, audience in inventory:
    rel = path.relative_to(ROOT)
    scope = path.parent.relative_to(ROOT) or Path(".")
    print(f"{rel}\tscope={scope}\taudience={audience}")

root_text = flat(root_agents.read_text(encoding="utf-8")) if root_agents else ""

# 1. Root authority declarations the rest of the instruction system relies on.
for label, needle in {
    "canonical-authority declaration": "canonical repository instruction authority",
    "explicit user precedence": "Explicit user instructions override repository instructions",
    "unresolved-conflict stop rule": "stop implementation, record a governance finding",
    "nested scope rule": "nested `AGENTS.md`",
    "higher-scope precedence": "higher-scope",
    "adapter deferral": "adapter",
}.items():
    if needle not in root_text:
        errors.append(f"root AGENTS.md: missing {label}")

# 2. High-risk normative concepts must live in the root authority, not only in adapters.
for label, patterns in {
    "public API exposure": [r"provider IDs, manifests, ExecutionBackend",
                            r"provider/backend/environment/storage-provider identity in public APIs"],
    "provider authority": [r"Capability Registry is the authority", r"provider resolution"],
    "testing/delivery": [r"Do not fetch, pull, push, publish, deploy", r"Do not weaken, disable, bypass"],
}.items():
    count = sum(len(re.findall(pattern, root_text, flags=re.I)) for pattern in patterns)
    if count < 1:
        errors.append(f"root AGENTS.md: missing normative {label} rule")

if re.search(r"may expose provider (?:ids|identit)", root_text, re.I):
    errors.append("provider/backend identity is authorized by an ambiguous public rule")
if re.search(r"provider.*identit(?:y|ies).*public API", root_text, re.I) and not re.search(r"remain internal unless", root_text, re.I):
    errors.append("provider/public API rule lacks internal-only exception")

# 3. Adapters defer to AGENTS.md and never carry independent normative or stale tool facts.
FORBIDDEN_ADAPTER_TOKENS = {
    "Frozen Boundaries": "independent frozen-boundary declaration",
    "Module Boundaries": "independent module-boundary declaration",
    "Do not push to remote": "push rule that ignores owner authorization",
    "Testcontainers PostgreSQL for all tests": "stale database-test claim (controlled PG18 launcher is required)",
    "Flyway V1-V4": "stale migration inventory",
    "H2 is permitted": "prohibited database engine",
}
for adapter in [*tool_adapters, *claude_adapters]:
    text = adapter.read_text(encoding="utf-8")
    rel = adapter.relative_to(ROOT)
    if "AGENTS.md" not in text:
        errors.append(f"{rel}: adapter must reference AGENTS.md")
    for token, meaning in FORBIDDEN_ADAPTER_TOKENS.items():
        if token in text:
            errors.append(f"{rel}: {meaning} ({token!r})")

# 4. Duplicate normative statements are forbidden across AGENTS files.
statements: dict[str, list[str]] = {}
for path in [p for p in [root_agents, *nested_agents] if p]:
    for line in path.read_text(encoding="utf-8").splitlines():
        normalized = re.sub(r"\s+", " ", line.strip().lower())
        if len(normalized) >= 32 and ("must " in normalized or "do not " in normalized or "may not " in normalized):
            statements.setdefault(normalized, []).append(str(path.relative_to(ROOT)))
for statement, owners in statements.items():
    if len(owners) > 1:
        errors.append(f"duplicate normative statement across AGENTS.md files: {statement!r} ({owners})")

# 5. Contradictory allow/deny pairs across every instruction surface.
all_text = flat("\n".join(path.read_text(encoding="utf-8") for path, _ in inventory))
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

if errors:
    print("FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print(f"PASS: {len(inventory)} instruction files inventoried; no governance conflicts detected")
