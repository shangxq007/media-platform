# Review Checklists

> **Last Updated:** 2026-05-18

## Frontend Component Checklist

### Editor Components

| # | Check | Status |
|---|-------|--------|
| 1 | Empty project guide renders | ✅ |
| 2 | Try demo project populates clip library and timeline | ✅ |
| 3 | Upload button works | ✅ |
| 4 | Clip library displays items | ✅ |
| 5 | Insert clip to timeline works | ✅ |
| 6 | Timeline selected clip state works | ✅ |
| 7 | Properties panel displays selected clip | ✅ |
| 8 | Preview updates with playhead | ✅ |
| 9 | Save button changes unsaved state | ✅ |
| 10 | Export panel validates timeline | ✅ |
| 11 | Export panel disables unavailable preset | ✅ |
| 12 | Submit render job shows job id | ✅ |
| 13 | Render job completed shows artifact | ✅ |
| 14 | Subtitle panel adds subtitle | ✅ |
| 15 | Effects panel applies effect | ✅ |
| 16 | ErrorState displays errorCode | ✅ |
| 17 | i18n message renders | ✅ |
| 18 | Artifact preview and download work | ✅ |

### User Portal

| # | Check | Status |
|---|-------|--------|
| 19 | Dashboard loads with overview | ✅ |
| 20 | My Projects page shows project list | ✅ |
| 21 | My Capabilities page shows tier features | ✅ |
| 22 | My Usage page shows statistics | ✅ |
| 23 | My Billing page shows billing info | ✅ |
| 24 | My Credits page shows wallet balance | ✅ |
| 25 | My Feedback page submits feedback | ✅ |
| 26 | Beta Features panel shows gated features | ✅ |

### Admin Console

| # | Check | Status |
|---|-------|--------|
| 27 | Admin console hidden from normal user | ✅ |
| 28 | Feature Flag Management page loads | ✅ |
| 29 | Feature Flag Editor creates/edits flags | ✅ |
| 30 | Feature Flag Rule Editor configures rules | ✅ |
| 31 | Feature Flag Evaluation Preview works | ✅ |
| 32 | Policy Simulation Panel shows decision chain | ✅ |
| 33 | Route Management page configures navigation | ✅ |
| 34 | Extension Management page loads | ✅ |
| 35 | Monitoring Status page shows health | ✅ |

## Backend API Checklist

### Render Pipeline

| # | Check | Status |
|---|-------|--------|
| 1 | Submit render job returns jobId | ✅ |
| 2 | Get job status returns current state | ✅ |
| 3 | Cancel job transitions to CANCELLED | ✅ |
| 4 | Retry job transitions to QUEUED | ✅ |
| 5 | Quota check rejects when exceeded | ✅ |
| 6 | Entitlement check rejects when not entitled | ✅ |
| 7 | Artifact registered on completion | ✅ |

### Entitlement & Billing

| # | Check | Status |
|---|-------|--------|
| 8 | Get capabilities returns tier info | ✅ |
| 9 | Create grant works | ✅ |
| 10 | Revoke grant works | ✅ |
| 11 | Extend grant works | ✅ |
| 12 | Create override works | ✅ |
| 13 | Export validation checks tier | ✅ |

### Feature Flags

| # | Check | Status |
|---|-------|--------|
| 14 | Create flag works | ✅ |
| 15 | Evaluate flag returns correct value | ✅ |
| 16 | Targeting rules match correctly | ✅ |
| 17 | Percentage rollout works | ✅ |
| 18 | Audit events recorded | ✅ |

### GraphQL

| # | Check | Status |
|---|-------|--------|
| 19 | Project query returns data | ✅ |
| 20 | Render job query returns data | ✅ |
| 21 | DataLoader batching works | ✅ |
| 22 | Query depth limit enforced | ✅ |
| 23 | Tenant scope injected | ✅ |

### NLQ

| # | Check | Status |
|---|-------|--------|
| 24 | SQL generation from natural language | ✅ |
| 25 | SQL safety validation works | ✅ |
| 26 | Scope isolation injected | ✅ |
| 27 | Result redaction works | ✅ |
| 28 | Chart suggestions returned | ✅ |

## Infrastructure Checklist

| # | Check | Status |
|---|-------|--------|
| 1 | Docker build succeeds | ✅ |
| 2 | Docker Compose starts all services | ✅ |
| 3 | Health check endpoint returns UP | ✅ |
| 4 | Database migrations run on startup | ✅ |
| 5 | Frontend served from backend static | ✅ |
| 6 | API docs accessible at /swagger-ui.html | ✅ |
