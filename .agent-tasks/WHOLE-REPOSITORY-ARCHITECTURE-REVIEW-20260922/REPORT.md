# Whole-repository architecture review and bounded integrated acceptance

Date: 2026-09-22

Repository: `media-platform`

Candidate: `819b73fb` (`main`)

Worktree state at review start and end: clean; no source changes made by this review.

## Scope

This review covers the current multi-module Gradle repository, the frontend and
contract tooling, current architecture guards, recent EP convergence changes on
`main`, and a bounded integrated test slice covering component assembly, social
publication, events, commerce, marketplace, health, federation, audit, usage and
storage ownership.

## Architecture assessment

The current runtime architecture is internally coherent for the reviewed scope.
The architecture drift guard completed with 42/42 checks passing. Its checks
covered canonical media/timeline/audio boundaries, provider and worker
separation, storage exposure, deferred OpenCue/Artifact-DAG/Spring-AI states,
EP31 version retirement, H7/H8 authority boundaries, color/image and
font/text ownership, and Phase 16/17 clean-forward rules.

The PFIRR1 remediation gate passed. It also passed the Artifact, Timeline merge,
GCR1/GCR2, jOOQ, product-layer retirement, Timeline effect/transition
canonicalization, C20 render-plan, authentication-authority, Phase 16, Phase 17,
and storage identity/placement sub-gates.

The module graph remains intentionally centralized at `platform-app`; domain
modules publish ports and contracts, while provider/runtime modules depend on
worker-fabric, sandbox and render-plan contracts. The scan found no new
unclassified runtime authority in the guarded surfaces. Existing cross-module
imports into render-plan and workflow adapters are the documented integration
contracts, not an unreviewed second authority.

## Integrated acceptance evidence

The following commands passed on the candidate:

- `./gradlew --no-daemon pfirr1RemediationCheck`
- `bash scripts/check-architecture-drift.sh`
- `./gradlew --no-daemon :platform-app:bootJar -x test`
- `./gradlew --no-daemon compileJava compileTestJava`
- Bounded `platform-app` integration slice with Podman's Docker-compatible
  socket: 128 tests, 128 passed, 0 failures, 0 errors, 0 skipped.

The bounded test slice covered component assembly, social publication and
scheduler ownership, event/outbox closure, commerce, marketplace, health,
federation owner reads, audit ownership, usage boundaries, storage ownership,
and admin-audit behavior.

The first test attempt without a container socket produced 115 tests with 84
environment failures (`Could not find a valid Docker environment`). The same
scope passed after starting the existing user Podman socket and setting only
`DOCKER_HOST` for that command. No proxy variable or local HTTP proxy was used.

## Acceptance gaps and risks

The repository is not fully release-accepted yet:

1. Document governance is `14/16` passed. DG-006 reports that the protected
   `schema-intent-contract.md` semantic body differs from its registered
   baseline. DG-011 reports four newly introduced broken links:
   `docs/runbook-five-capabilities.md:114`,
   `docs/architecture/blueprint/capability-opening-blueprint.md:619`, and the
   Flyway V1 links at lines 400 and 149 in the two operations guides.
2. API contract governance passed Spectral but could not execute the additive
   `oasdiff` check because the pinned binary is unavailable in the environment.
3. The Semgrep architecture matrix could not execute because `uvx` is not
   installed. The repository's Gradle/Python architecture guards still passed.
4. Frontend lint/typecheck/tests/build were not executable because
   `frontend/node_modules` is absent (`eslint: command not found`). This leaves
   frontend acceptance open even though no frontend source changed in this
   review.

These are governance/tooling and documentation closure gaps, not failures in
the bounded backend runtime slice. They prevent an unconditional whole-
repository release acceptance.

## Decision

**Bounded integrated acceptance: CONDITIONAL PASS.**

The current `main` candidate is acceptable for the reviewed backend/runtime
convergence slice. Whole-repository acceptance remains blocked until the two
document-governance failures are corrected or explicitly re-baselined, and the
missing `oasdiff`, Semgrep/`uvx`, and frontend dependency prerequisites are
restored and rerun.
