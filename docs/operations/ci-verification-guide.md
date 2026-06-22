# CI Verification Guide

> **Last Updated:** 2026-06-22
> **Source:** `.github/workflows/ci.yml`

## CI Pipeline Overview

The project uses a single GitHub Actions workflow (`ci.yml`) with 5 jobs:

| Job | Trigger | Purpose |
|-----|---------|---------|
| `backend` | push (all branches), PR | Run all backend tests, build bootJar, Docker smoke check |
| `frontend` | push (all branches), PR | Lint, test, build frontend |
| `images` | push to main only | Build + push 3 Docker images to GHCR, create staging GitOps PR |
| `promote-production` | workflow_dispatch only | Validate image tag, create production GitOps PR |
| `deploy-staging` | workflow_dispatch only | Manual staging deployment |

## Local Verification Sequence

To reproduce CI locally, run these commands in order:

### 1. Backend Tests

```bash
./gradlew --no-daemon test
```

This runs all module tests including:
- `ModularityTest` — module boundary verification
- `ProductionSafetyValidatorTest` — production safety checks
- `FlywaySchemaIntegrationTest` — Flyway schema validation (requires Docker)
- All unit and integration tests across 35 modules

**Known issue:** `spring-ai-adapter` has a compilation error (`TenantLitellmKeyService` not found). This may cause `./gradlew test` to fail on the `spring-ai-adapter` module. Run targeted tests if this occurs:

```bash
./gradlew test -x :spring-ai-adapter:test
```

### 2. BootJar Smoke Check

```bash
./gradlew --no-daemon :platform-app:bootJar -x test
```

Verifies the Spring Boot JAR builds successfully.

### 3. Docker Build Smoke Check

```bash
docker build -t media-platform:ci .
```

Verifies the multi-stage Docker build works (Node 22 → Gradle 9.1/JDK 25 → Eclipse Temurin 25 JRE).

### 4. Frontend Tests

```bash
cd frontend
npm ci
npm run lint --if-present
npx vitest run
npm run build
```

### 5. Module Boundary Verification (Targeted)

```bash
./gradlew :platform-app:test --tests '*ModularityTest*'
```

Verifies zero unexpected Modulith violations. Currently allows 2 violations:
- `identity → artifact`
- `identity → storage`

### 6. Quota and Render Tests

```bash
./gradlew :quota-billing-module:test --tests '*Quota*'
./gradlew :render-module:test
```

## CI Triggers

| Event | What Runs |
|-------|-----------|
| Push to any branch | `backend` + `frontend` |
| Pull request | `backend` + `frontend` |
| Push to main | `backend` + `frontend` + `images` (build + push + staging PR) |
| workflow_dispatch (staging) | `deploy-staging` |
| workflow_dispatch (production) | `promote-production` |

**Note:** `paths-ignore` excludes `gitops/**`, `docs/gitops.md`, and `*.md` from triggering CI. Documentation-only changes do not trigger backend/frontend tests.

## Images Built

| Image | Dockerfile | Purpose |
|-------|-----------|---------|
| `platform-api` | `Dockerfile` | Main Spring Boot application |
| `platform-render-worker` | `remote-render-worker/Dockerfile` | Remote render worker |
| `platform-sandbox-worker` | `sandbox-worker/Dockerfile` | Sandbox execution worker |

All images are pushed to `ghcr.io/<owner>/` with tag `git-<short-sha>`.

## Staging Promotion

When `images` job completes on main push:
1. Updates `gitops/staging/` manifests with new image tags
2. Validates staging readiness and egress smoke config
3. Creates a PR to merge staging manifests
4. ArgoCD auto-syncs staging namespace on PR merge

## Production Promotion

Requires manual `workflow_dispatch`:
1. Validates image tag (must not be `latest` or `dev`)
2. Updates `gitops/production/` manifests
3. Validates production readiness and egress smoke config (strict)
4. Creates a PR with manual approval checklist
5. ArgoCD requires manual sync (no auto-sync for production)

## Common Failures

| Failure | Cause | Fix |
|---------|-------|-----|
| `spring-ai-adapter:compileJava` | `TenantLitellmKeyService` not found | Known issue; exclude from test run |
| `ModularityTest` failure | Module boundary violation | Check `docs/modulith-debt-register.md` |
| `FlywaySchemaIntegrationTest` | Docker not available | Requires Docker for Testcontainers |
| Frontend lint failure | ESLint errors | Run `npm run lint:fix` |
| `RenderNatronEffectsIT` | Natron binary not installed | Pre-existing; skip or install Natron |

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/validate-production-readiness.sh` | Validate GitOps manifests |
| `scripts/verify-egress-smoke-config.sh` | Validate egress proxy config |
| `scripts/update-gitops-manifests.sh` | Update GitOps manifests with image tags |
| `scripts/local-test.sh` | Local test runner |
| `scripts/smoke-local.sh` | Local smoke tests |
| `scripts/smoke/e2e-render-flow.sh` | End-to-end render flow smoke test |
