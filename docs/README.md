# Platform Documentation

## Documentation Structure

### architecture/blueprint/
Target architecture documents. These describe the **intended design**, not the current implementation.

### architecture/current/
Current system state documents. These describe **what is actually implemented** and validated.

### operations/
Runbooks and operational guides. These contain **commands and procedures** for running the system.

### review/
Historical reports and validation results. These are **point-in-time snapshots** of system state.

### roadmap/
Future work and planned improvements. These describe **what is not yet implemented**.

### archive/
Deprecated or historical documents. These are **not current truth** and should not be used as reference.

---

## Document Metadata

Every major document should include metadata:

```yaml
---
status: blueprint | current | runbook | report | roadmap | deprecated
last_verified: YYYY-MM-DD
scope: preview | staging | production | future | all
truth_level: target | implemented | partially-implemented | historical
owner: platform
---
```

---

## Current Status (2026-06-18)

- **Manual Preview**: ✅ READY
- **Staging Review**: ✅ READY  
- **Production**: ⚠️ NOT READY

See [release-candidate-readiness-2026-06-17.md](review/release-candidate-readiness-2026-06-17.md) for details.

---

## Key Documents

### Getting Started
- [Current System State](architecture/current/current-system-state.md)
- [Current Module Status](architecture/current/current-module-status.md)
- [Current Startup Profiles](architecture/current/current-startup-profiles.md)

### Operations
- [PostgreSQL Preview/Staging Runbook](operations/postgres-preview-staging-runbook.md)
- [Security Preview/Safe-Mode Runbook](operations/security-preview-safe-mode-runbook.md)
- [Flyway Baseline Runbook](operations/flyway-baseline-runbook.md)

### Architecture
- [System Blueprint](architecture/blueprint/system-blueprint.md)
- [Module Blueprints](architecture/blueprint/)
  - [Render Pipeline](architecture/blueprint/module-blueprint-render.md)
  - [Artifact & Storage](architecture/blueprint/module-blueprint-artifact-storage.md)
  - [Automation & Plugin](architecture/blueprint/module-blueprint-automation-plugin.md) *(blueprint only)*
  - [AI Provider](architecture/blueprint/module-blueprint-ai-provider.md) *(blueprint only)*
  - [Security & Identity](architecture/blueprint/module-blueprint-security-identity.md)
  - [Observability](architecture/blueprint/module-blueprint-observability.md)
- [Current Known Gaps](architecture/current/current-known-gaps.md)

### Review
- [Release Candidate Readiness](review/release-candidate-readiness-2026-06-17.md)
- [Manual Preview Smoke Report](review/manual-preview-smoke-report-2026-06-17.md)
- [Comprehensive Issue Report](review/comprehensive-issue-report-2026-06-16.md)

### Roadmap
- [Technical Debt Roadmap](roadmap/technical-debt-roadmap.md)
- [Automation/Plugin Platform Roadmap](roadmap/automation-plugin-platform-roadmap.md)
- [AI Provider Ecosystem Roadmap](roadmap/ai-provider-ecosystem-roadmap.md)
- [Render Pipeline Roadmap](roadmap/render-pipeline-roadmap.md)

---

## Important Notes

> **Blueprint documents describe target architecture. Current state documents describe implemented reality.**

- H2 is unsupported (PostgreSQL only)
- Spring AI is isolated and not active in runtime
- Automation/Plugin platform is blueprint only
- See [Current Known Gaps](architecture/current/current-known-gaps.md) for implementation gaps
