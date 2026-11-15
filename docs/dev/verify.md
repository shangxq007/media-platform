# Verification workflow (L1 / L2 / L3)

> **Status:** CURRENT (2026-11-15). Entry point: [`scripts/dev/verify`](../../scripts/dev/verify).

This defines how much verification a change needs and how to run it without rediscovering commands.
Agents must run the narrowest level that proves the change, and never start a heavy level while its
preflight is blocked.

## Levels

| Level | Proves | Runs | Typical use |
|---|---|---|---|
| **L1** | the change compiles and affected behaviour is intact | affected module compile + targeted unit/contract tests + affected guards | any source, test, guard or documentation change |
| **L2** | real database/provider/application integration | controlled PostgreSQL18 selection (JUnit targets through the protected launcher) | persistence, jOOQ, Artifact, provider, application wiring changes |
| **L3** | the real product path works end to end | API + Temporal + Worker + FFmpeg + Chrome + real media result | execution-path, runtime-binding or media-behaviour changes; release candidates |

L1 is the default for documentation, governance and local refactor work. L2 is required when the
change touches SQL, jOOQ, persistence, Artifact, provider or application composition. L3 is required
when the change can alter execution, provider selection, media output or deployment behaviour.

## Preflight first

```sh
python3 scripts/dev/verify preflight --level l1   # cheap, no build
```

`preflight` inspects commit/tree/branch, tool availability, rootless namespaces, the worker FFmpeg
configuration (path, `drawtext`, `overlay`, `libx264`, `qtrle`, font), PG18/Temporal/OIDC
reachability and evidence-directory writability. It prints `PASS` or `BLOCKED` with explicit blockers.

- Do not start L3 when preflight is `BLOCKED`.
- When the same environment blocker repeats without an environment change, stop retrying and report it.
- `preflight --level l3` also requires `FFMPEG_WORKER_FONT`; a worker without a font fails subtitle
  rasterization closed.

## Commands

```sh
# L1: one module's affected tests (gradle wrapper, no daemon)
python3 scripts/dev/verify l1 --module artifact-module --tests '*ProvenanceErrorContextTest*'

# Guards: architecture drift, instruction governance (+self-test), jOOQ parity, OIDC bundle
python3 scripts/dev/verify guards

# L2: the controlled PostgreSQL18 selection (protected launcher inputs required)
python3 scripts/dev/verify l2 \
  --binding /private/owned-container-binding.json \
  --password-file /private/synthetic-test-password \
  --classpath-file /private/platform-app-test-classpath.txt

# L3: real API/Worker/PG18/Temporal/FFmpeg/browser E2E into a new external directory
python3 scripts/dev/verify e2e --output /external/new-first-render
```

`l2` reuses the existing controlled launcher (`scripts/test-infra pg18-local`) and fails closed with
`L2 BLOCKED` when the binding/credential/classpath inputs are absent. It does not invent a second
PostgreSQL runtime, publish ports or accept ad-hoc JDBC URLs. The launcher contract is in
[scripts/testinfra/README.md](../../scripts/testinfra/README.md).

`e2e` wraps [`scripts/dev/first-render`](../../scripts/dev/first-render); First Render is an E2E
scenario over the formal `POST /api/render-jobs` path, not a product entry.

## Evidence

Every run writes a manifest under `VERIFY_EVIDENCE` (default: sibling
`<repository>-evidence/verify/<timestamp>/`) containing the exact commit, tree, dirty paths, commands,
exit codes, log paths and tool versions. Record in the task:

- commit and tree;
- command and exit code;
- tool versions from the manifest;
- log locations;
- explicit `BLOCKED` reasons when a level cannot run.

Reused or overlapping evidence must be labelled. Governance-only changes must not claim product or
runtime validation.

## Selecting the minimum level

| Change | Minimum |
|---|---|
| documentation / governance / instruction files | L1 (guards + instruction governance) |
| test-only fix | L1 (affected module tests) |
| persistence / jOOQ / Artifact / provider configuration | L2 |
| execution path, worker binding, media output, deployment behaviour | L3 |
