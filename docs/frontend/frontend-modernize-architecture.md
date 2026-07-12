# Frontend Modernization Architecture — media-platform

**Date:** 2026-07-01
**Status:** PLANNED
**Authority:** FRONTEND-MODERNIZE.0

---

## Current Frontend Inspection

### Stack (Existing)

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | React | 19 |
| Build | Vite | 6 |
| Language | TypeScript | 5.7 |
| Routing | TanStack Router | 1.90 |
| Data fetching | TanStack Query | 5.60 |
| State | Zustand | 5.0 |
| Validation | Zod | 4.3 |
| CSS | Tailwind CSS | 3.4 |
| API client | Axios | 1.7 |
| Preview | Remotion Player | 4.0 |
| Monitoring | Sentry | 10.53 |
| Auth | oidc-client-ts | 3.5 |

### Source Structure

```
frontend/src/
├── api/              # API layer (Axios, contracts)
│   ├── admin/        # Admin APIs
│   ├── contracts/    # API contracts
│   └── guard/        # API guards
├── app/              # App shell
├── auth/             # Auth/session
├── components/       # 17 components
│   ├── artifacts/
│   ├── assets/
│   ├── error/
│   ├── render-jobs/
│   └── smoke-editor/
├── config/           # Configuration
├── domain/           # Domain models
├── editor/           # Editor components
│   ├── captions/
│   ├── inspector/
│   ├── playback/
│   └── templates/
└── timeline/         # Timeline state
    └── store/        # Zustand store
```

### Assessment

| Aspect | Status | Notes |
|--------|--------|-------|
| Framework choice | ✅ Good | React 19 + Vite 6 is modern |
| Routing | ✅ Good | TanStack Router is solid |
| Data fetching | ✅ Good | TanStack Query is solid |
| State management | ✅ Good | Zustand is lightweight |
| Validation | ✅ Good | Zod v4 is current |
| CSS | ⚠️ Upgrade | Tailwind v3 → v4 recommended |
| API client | ⚠️ Consider | Axios → fetch/TanStack Query |
| Component library | ⚠️ Missing | No design system/primitives |
| Editor | ⚠️ Complex | Custom editor is domain-specific |

### What Can Be Discarded

- Old Tailwind v3 config (migrate to v4)
- Axios layer (replace with fetch + TanStack Query)
- Scattered component styles (consolidate to design tokens)

### Contracts to Preserve

- API endpoint contracts (backend API is source of truth)
- Zod validation schemas
- Domain model types
- Timeline state model

---

## New Frontend Baseline

| Technology | Classification | Notes |
|-----------|---------------|-------|
| React 19 | REQUIRED | Already in use |
| Vite 6 | REQUIRED | Already in use |
| TypeScript 5.7+ | REQUIRED | Already in use |
| TanStack Router | REQUIRED | Already in use |
| TanStack Query | REQUIRED | Already in use |
| Zustand | REQUIRED | Already in use |
| Zod | REQUIRED | Already in use |
| **Tailwind CSS v4** | RECOMMENDED | Upgrade from v3 |
| Radix UI | OPTIONAL | Accessible primitives |
| shadcn/ui | OPTIONAL | After design tokens decided |
| Remotion Player | REQUIRED | Preview rendering |
| Sentry | REQUIRED | Error monitoring |
| **fetch API** | RECOMMENDED | Replace Axios |

---

## Frontend Architecture

### App Shell

```
App
├── AuthProvider
├── QueryClientProvider
├── RouterProvider
│   ├── Layout
│   │   ├── Sidebar
│   │   ├── Header
│   │   └── Outlet
│   └── ErrorBoundary
└── Sentry.ErrorBoundary
```

### Routing

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | Dashboard | Project overview |
| `/projects` | ProjectList | All projects |
| `/projects/:id` | ProjectDetail | Project view |
| `/projects/:id/editor` | Editor | Timeline editor |
| `/projects/:id/render-jobs` | RenderJobs | Job list |
| `/products/:id` | ProductDetail | Product/artifact view |
| `/settings/providers` | ProviderSettings | Provider config |
| `/settings/workers` | WorkerSettings | Worker config |

### API Client Boundary

```
UI Components
  → TanStack Query hooks
    → API client (fetch)
      → Backend REST API
```

- Replace Axios with fetch + TanStack Query
- Zod schemas validate all API responses
- API contracts defined in `api/contracts/`

### Editor State Model

```
TimelineStore (Zustand)
├── tracks: Track[]
├── clips: Clip[]
├── captions: CaptionTemplate[]
├── selected: Selection
└── history: UndoStack
```

### VS.0 Integration

| VS.0 Output | Frontend Consumption |
|-------------|---------------------|
| TimelineEditCommand | Editor command panel |
| CaptionTemplate | Caption style panel |
| Provider binding | Provider settings view |
| FFmpeg plan | Render job panel |
| Product output | Product/preview viewer |

---

## Guardrails

1. Do not mix frontend modernization into backend/render VS tasks
2. Do not migrate old frontend incrementally
3. Do not let UI state become canonical Timeline model
4. Do not expose provider/storage internals in UI
5. Do not expose local paths
6. Do not introduce unreviewed auth model
7. Do not enable Remotion production dispatch

---

*Generated by Hermes Agent — FRONTEND-MODERNIZE.0*
