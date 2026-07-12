# Architecture Maps

**Status:** ACTIVE
**Authority:** ARCH-MAP.0 / ADR-027

---

## Tool Selection

**Primary:** LikeC4 (v1.58.0 via npx)

### Why LikeC4

- Architecture diagrams as code — version-controlled, diffable
- Generates multiple output formats (Mermaid, D2, DOT, PNG, HTML)
- Simple DSL, low maintenance overhead
- No proprietary lock-in — plain text files
- Integrates with CI for validation

### Why Not Structurizr as Primary

- Structurizr DSL is more verbose
- No direct LikeC4-to-Structurizr export assumed
- Structurizr maintained separately as optional high-level view if needed

### Export Strategy

| Format | Path | Purpose |
|--------|------|---------|
| Mermaid | `exports/mermaid/` | GitHub markdown embedding |
| D2 | `exports/d2/` | Alternative diagram format |
| DOT | `exports/dot/` | Graphviz export |
| PNG | `exports/png/` | Static images for reports/docs |

### Authority Level

LikeC4 maps are **Level 4: Visual/Derived** — NOT the canonical source of truth.

Canonical sources:
1. AGENTS.md
2. current-system-state.md
3. production-safety.md
4. blueprint
5. ADRs

LikeC4 diagrams must not invent components not present in docs/code.
