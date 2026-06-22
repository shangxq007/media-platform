# Document Lifecycle Policy

> **Last Updated:** 2026-06-22  
> **Scope:** All documentation in `docs/`, `AGENTS.md`, `.kilo/agents/main.md`

## Document States

| State | Description | Header Format |
|-------|-------------|---------------|
| **Current** | Actively maintained, verified against code | `status: current` in YAML frontmatter |
| **Blueprint** | Target architecture, not yet implemented | `status: blueprint` in YAML frontmatter |
| **Report** | Point-in-time snapshot, never edited after creation | `status: report` in YAML frontmatter |
| **Archived** | Superseded, historical, no longer current truth | `> **Status:** Archived (YYYY-MM-DD)` in blockquote |
| **Deprecated** | Marked for removal, still accessible | `> **Status:** Deprecated` in blockquote |

## Lifecycle Transitions

```
Current → Updated (when code changes)
Current → Deprecated (when superseded)
Current → Archived (after 30 days of deprecation)
Blueprint → Current (when implemented)
Blueprint → Archived (when abandoned)
Report → Archived (after 30 days)
```

## Creation Rules

1. Every new document must include metadata:
   - `Last Updated: YYYY-MM-DD`
   - `Last Validated Against Code: YYYY-MM-DD` (for current docs)
   - `status` field in YAML frontmatter or blockquote header

2. Review reports are immutable after creation — create a new report instead of editing

3. Blueprints include "Reality Check" sections validated against code

## Update Rules

| Change Type | Must Update |
|-------------|------------|
| New module added | `AGENTS.md`, `docs/README.md` module count |
| NamedInterface changed | `docs/modulith-debt-register.md` |
| Flyway migration added | `docs/operations/flyway-migration-guide.md` |
| New render provider | `docs/render/provider-roadmap.md` |
| Security config changed | `docs/production-safety.md` |
| New application profile | `docs/architecture/current/current-startup-profiles.md` |
| Frontend framework change | `AGENTS.md`, `docs/README.md`, `04-frontend-architecture.md` |

## Archival Rules

1. Add archive header to file top (do not delete content)
2. Include: Status, Reason, Superseded By, Do not use as current reference
3. Never edit archived content
4. Never delete archived files

## Validation Rules

1. Quarterly: validate all Tier 0-1 documents against code
2. On major release: regenerate `docs/architecture/current/` from code
3. CI check: verify `AGENTS.md` exists and module count matches `settings.gradle.kts`

## Agent Rules

1. Agents must not modify documents outside their assigned module scope
2. Agents must not edit archived documents
3. Agents must not create documents without metadata headers
4. Agents must run `ModularityTest` after any `package-info.java` change
