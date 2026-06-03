# Media Platform — Documentation Hub

> **Version:** 0.2.0-SNAPSHOT
> **Last Updated:** 2026-05-18
> **Documentation Rebuild:** Prompt 66

---

## 📖 Reading Path

| Role | Start Here |
|------|-----------|
| New developer | `00-overview/` → `01-architecture/` → `11-development/` |
| Backend engineer | `01-architecture/` → `02-modules/` → `03-media-rendering/` |
| Frontend engineer | `04-frontend/` → `06-api/` |
| DevOps / SRE | `10-deployment-ops/` → `09-observability-quality/` |
| Tech lead / Reviewer | `review/05-architecture-evaluation.md` → `review/01-production-blockers.md` |
| Product / QA | `overview/` → `frontend/` → `review/03-review-checklists.md` |

---

## 📂 Directory Structure

```
docs/
├── README.md                          # ← You are here
├── index.md                           # Full document index
├── overview/                          # Project overview & status
├── architecture/                      # Architecture documentation
│   ├── 01-system-architecture.md
│   ├── 02-backend-architecture.md
│   ├── 03-module-architecture.md
│   ├── 04-frontend-architecture.md
│   ├── 05-request-flows.md
│   ├── 06-data-architecture.md
│   ├── 07-architecture-decisions.md
│   └── 08-deployment-architecture.md
├── 02-modules/                        # Module reference
│   ├── 01-core-modules.md
│   ├── 02-media-modules.md
│   ├── 03-business-modules.md
│   └── 04-platform-modules.md
├── 03-media-rendering/                # Render pipeline & media
│   ├── 01-render-pipeline.md
│   ├── 02-provider-registration.md
│   ├── 03-provider-roadmap.md
│   ├── 04-media-probe.md
│   ├── 05-subtitle-burn-in.md
│   └── 06-vfx-compositing-ecosystem-selection.md
├── 04-frontend/                       # Frontend documentation
│   ├── 01-editor-workbench.md
│   ├── 02-editor-timeline.md
│   ├── 03-editor-export.md
│   ├── 04-editor-upload.md
│   ├── 05-user-portal.md
│   ├── 06-admin-console.md
│   ├── 07-frontend-api.md
│   └── 08-feature-flag-ui.md
├── 05-access-entitlement-billing/      # Access, entitlement, billing
│   ├── 01-entitlement.md
│   ├── 02-access-decision.md
│   ├── 03-feature-flag-governance.md
│   ├── 04-configurable-navigation.md
│   ├── 05-cost-control.md
│   ├── 06-reconciliation.md
│   └── 07-billing-models.md
├── 06-api/                            # API strategy
│   ├── 01-api-strategy.md
│   ├── 02-openapi.md
│   └── 03-graphql.md
├── 07-prompt-ai-nlq/                  # Prompt, AI, NLQ
│   ├── 01-prompt-engineering.md
│   ├── 02-prompt-extensions.md
│   └── 03-nlq.md
├── 08-extension-platform/             # Dynamic extensions
│   ├── 01-extension-platform.md
│   └── 02-sandbox-runtime.md
├── 09-observability-quality/          # Monitoring & quality
│   ├── 01-observability.md
│   ├── 02-third-party-monitoring.md
│   ├── 03-problematic-data.md
│   ├── 04-anomaly-detection.md
│   └── 05-feedback-monitoring.md
├── 10-deployment-ops/                 # Deployment & ops
│   ├── 01-deployment.md
│   ├── 02-deployment-checklist.md
│   ├── 03-demo-script.md
│   └── 04-rollback.md
├── 11-development/                    # Development standards
│   ├── 01-code-style.md
│   ├── 02-module-structure.md
│   ├── 03-security.md
│   └── 04-error-handling.md
├── 12-review/                         # Review & blockers
│   ├── 01-production-blockers.md
│   ├── 02-technical-debt.md
│   ├── 03-review-checklists.md
│   ├── 04-documentation-audit-report.md
│   └── 05-architecture-evaluation.md  # Architecture assessment (2026-05-20)
└── archive/                           # Historical documents
    └── README.md
```

---

## 🔑 Key Status Markers

| Marker | Meaning |
|--------|---------|
| ✅ Implemented | Fully implemented and tested |
| ⚠️ Partial | Core features implemented, some stubs remain |
| 🔧 Stub / Mock | Infrastructure ready, real implementation pending |
| 📋 Future Work | Planned but not yet implemented |
| 🔴 Production Blocker | Must fix before production |
| 🧪 Needs Human Review | Requires manual verification |

---

## 📊 Project Snapshot

| Metric | Value |
|--------|-------|
| Total Gradle Modules | 30 |
| Java Source Files | ~1050+（`platform/` 下，见 [review/05-architecture-evaluation.md](review/05-architecture-evaluation.md)） |
| Backend Test Files | 54+ |
| Backend Tests | ~340+ |
| Frontend Test Files | 78+ |
| Frontend Tests | 639+ |
| Error Codes | 60+ |
| Flyway Migrations | 17 |
| Database Tables | 28+ |
| Prompts Completed | 66 |

---

## 🔴 Active Production Blockers

1. **No Authentication** — No Spring Security filter chain for production
2. **No Tenant Isolation** — TenantContext not enforced at data layer
3. **Payment Stubs** — All payment providers are Noop
4. **AI Stub** — StubChatProvider, no real model integration
5. **OpenFeature Remote Provider** — LocalFeatureFlagProvider is in-memory only

See `12-review/01-production-blockers.md` for full details.

---

## 📝 License

Internal project. All rights reserved.
