# Composition Foundation Correction Handoff

This correction binds composition reads and writes to `CanonicalActorResolver` plus `WorkspaceQueries`. A requested workspace is only a selection; Identity verifies the active tenant membership and rejects missing, inactive, cross-tenant, stale, or ambiguous membership results.

Composition drafts and validation snapshots use `CompositionRepository` and the Flyway schema. Draft writes use a revision predicate. Published values are copied into `composition_version`, whose database trigger rejects update and delete, while publish uses a transaction and a conditional draft transition.

Validation now intersects requested execution modes with capability modes and continues to fail closed for missing or unavailable capabilities, contract mismatches, assets, entitlements, malformed graph edges, cycles, and invalid ranges. The public contract remains provider-neutral. No provider, backend, worker, or topology identity is serialized.

This acceptance correction adds deterministic typed issue codes and paths for port/contract compatibility, cardinality, unreachable and terminal graph nodes, parameter schema/default/range checks, application execution-mode checks, and version-range checks. Resource checks now go through `CompositionResourceAuthority`, which delegates to the canonical artifact and entitlement authorities and enforces tenant/workspace scope, media/kind constraints, entitlement decisions, and quota estimates. Production composition services do not use placeholder asset or entitlement sets.

Application draft reopening restores the backend document, including selected workflows, requirements, execution modes, lifecycle and revision. Publish-readiness uses the application validator and rechecks referenced workflows and authoritative resources. The frontend exposes capability selection, application reopening, asset and entitlement editing, quota estimates, validation results, and 401/403/409 recovery.

Fresh evidence for this correction: `./gradlew :composition-module:compileJava :composition-module:compileTestJava` passed; hermetic PostgreSQL `scripts/test/podman-hermetic.sh run ./gradlew :composition-module:test --no-build-cache` passed 11/11 tests; frontend `npm run typecheck`, `npm run lint` (0 errors, existing warnings), focused Vitest (9/9), and `npm run build` passed. OpenAPI export was attempted but blocked because `http://localhost:8080` was not running; the checked-in preview was therefore not refreshed. Pinned oasdiff and Semgrep availability remain environment-gated and are not claimed as passed.

The architecture guard must be run against a committed candidate tree because H7 binds exact Git blobs; the pre-commit worktree run reports the expected `CompositionService.java` bound-tree mismatch. No production execution, external provider, or deployment validation was performed.

The frontend composition route is workspace-bound, passes the session-selected workspace to the catalog, handles retired sessions and 401/403 responses, and is reachable from both the existing developer route and `/w/{workspaceId}/composition`.

PVE, Temporal, Storage, FFmpeg/BMF, external providers, worker topology, production configuration, deployment, and real execution remain NOT_RUN.
