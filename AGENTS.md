# P2O.0g — RenderExecutionPlan-to-CJSL Mapping Design with Storage Strategy Boundary

You are working in the `media-platform` repository.

Use Claude Code as a single controlled architecture/design/documentation agent.

Repository documentation and repository state are the single source of truth.

Do not infer architecture from previous conversations.

Before implementation, verify that this task file is exactly:

```text
P2O.0g — RenderExecutionPlan-to-CJSL Mapping Design with Storage Strategy Boundary
```

If the task id is not exactly P2O.0g, stop immediately and report `TASK_MISMATCH`.

Do not execute R-series tasks.

Do not execute TimelineRevision Render API tasks.

Do not continue P2O.0f runtime execution unless explicitly needed only as evidence review.

Keep chat output compact.

Write detailed inventories, mapping notes, design decisions, examples, safety checks, and final report to files.

Do not print full logs, full diffs, generated source files, generated docs, generated configs, generated CJSL specs, or final report in chat.

Do not push, merge, or deploy.

## Mission

P2O.0f validated OpenCue local Docker execution behavior:

```text
Cuebot gRPC submission: PASS
CJSL XML format: DISCOVERED
Multi-frame jobs: PASS
Chunking: PASS
Multi-layer jobs: PASS
Failure visibility: PASS
Dependency syntax: BLOCKED / NOT_SUPPORTED_BY_CURRENT_CJSL_DTD
```

P2O.0g must now convert those findings into a platform-side mapping design:

```text
RenderExecutionPlan
  ↓
OpenCueJobSpecDraft
  ↓
CJSL XML
  ↓
Cuebot gRPC submission boundary
```

This task must also answer the storage question:

```text
If shared path is not available, how should OpenCue workers access inputs and return outputs?
```

The expected design direction is:

```text
Local / PVE smoke:
  shared path is acceptable

Production / cross-host / cross-provider:
  object storage + worker local materialization is preferred
```

P2O.0g is a design and skeleton task.

This is not PVE yet.

This is not production OpenCue adapter.

This is not live RenderExecutionPlan execution.

This is not ProductRuntime implementation.

This is not StorageRuntime implementation.

This is not public API.

This is not Artifact DAG.

This is not cross-service-provider execution implementation.

## Current Baseline

Completed before this task:

```text
P2L.0 — Local Explicit Render Smoke Harness
P2L.1 — BasicRenderPlan-to-Local-Runner Bridge
P2L.2 — Local Caption Overlay Smoke
P2L.3 — Real Media Source Materialization

P2O.0a — Local Docker OpenCue Shared-Path Smoke
P2O.0b — Local Docker Cuebot/RQD Runtime Smoke Preparation
P2O.0c — OpenCue Image Selection and Runtime Readiness
P2O.0d — Local Docker OpenCue Job Submission Smoke Fallback
P2O.0e — Cuebot gRPC Job Submission Discovery and Minimal Client Spike
P2O.0f — OpenCue Multi-Frame / Multi-Layer / Failure Visibility Validation
```

Important P2O.0e findings:

```text
Cuebot port: 8443
gRPC reflection: NOT_AVAILABLE
Submission service: job.JobInterface
Submission method: LaunchSpecAndWait
Request: JobLaunchSpecAndWaitRequest { string spec = 1; }
Spec format: CJSL XML
DTD: cjsl-1.15.dtd
```

Important P2O.0f findings:

```text
CJSL requires DTD declaration.
Facility must match RQD facility.
Layer type is "Render".
Cores: 100 = 1 core.
Memory format: "256m".
Multi-frame uses <range>N-M</range> and <chunk>K</chunk>.
RQD exposes CUE_FRAME.
Multiple <layer> elements work.
Failure visibility works: DEAD frames and exit codes are observable.
CJSL dependency syntax is not supported by current DTD.
Job name may be transformed by Cuebot.
Some job state propagation may need careful polling.
```

## Critical Architecture Decisions

OpenCue remains:

```text
OpenCue = ExecutionEnvironment
OpenCue != Provider
OpenCue != ExecutionBackend
OpenCue does not own visual capability semantics
OpenCue does not replace ProviderBindingPlan
OpenCue does not require Artifact DAG
```

Dependency handling decision:

```text
Do not assume CJSL can express RenderExecutionPlan DAG dependencies.

Because current CJSL dependency syntax is blocked by DTD, platform-side orchestration should manage dependencies by submission ordering, stage splitting, and status checks.

OpenCue may execute frame/layer work units, but platform orchestration owns cross-stage dependency semantics.
```

Storage decision:

```text
Shared path is valid for local Docker / single PVE / controlled LAN smoke.

If shared path is not available, use object storage + worker local materialization.

OpenCue is not the storage system.

StorageRuntime owns input/output storage semantics.

ProductRuntime owns product lifecycle and product dependency semantics.
```

## Strict Scope

This task may:

* Inspect P2O.0a through P2O.0f docs, examples, scripts, reports, CJSL specs, and runbooks.
* Create mapping design docs.
* Create DTO/domain skeletons only if clearly useful and low risk.
* Create example CJSL outputs from hypothetical RenderExecutionPlan examples.
* Create examples for shared-path and object-storage materialization strategies.
* Create fail-closed mapping rules.
* Create safety rules for command generation.
* Create docs for dependency handling without CJSL depends.
* Create docs for storage alternatives when shared path is unavailable.
* Update current architecture docs and production safety docs.
* Run documentation/safety grep checks.
* Run compile/tests only if Java skeletons are introduced.

This task must not:

* Implement production OpenCue adapter.
* Submit real jobs to Cuebot.
* Start PVE work.
* Add Cuebot client to application runtime.
* Add grpcurl/proto dependency to application runtime.
* Add public REST controllers.
* Add public API endpoints.
* Add database tables.
* Add Flyway migrations.
* Persist OpenCue jobs in application DB.
* Create platform RenderJob runtime execution.
* Create Product.
* Call ProductRuntime.
* Call StorageRuntime.
* Implement object storage upload/download logic.
* Implement cross-service-provider execution.
* Implement cross-cloud shared filesystem.
* Implement Artifact DAG.
* Implement incremental render.
* Implement cache reuse.
* Execute arbitrary provider commands in application code.
* Expose raw FFmpeg filtergraphs through public API.
* Expose raw shell commands through public API.
* Generate commands from untrusted user input.
* Call Remotion.
* Use vedit.
* Use pyvedit.
* Use OpenTimelineIO runtime.
* Execute Node/npm/npx/pnpm/yarn.
* Install packages automatically on host.
* Download large binaries into repository.
* Push, merge, or deploy.

## Required Design Outcomes

P2O.0g must produce a clear platform design for:

```text
1. RenderExecutionPlan to OpenCue job/layer/frame mapping.
2. CJSL generation boundary.
3. Multi-frame mapping.
4. Chunking policy.
5. Multi-layer mapping.
6. Dependency handling without CJSL depends.
7. Failure visibility mapping.
8. Retry/relaunch boundary.
9. Storage strategy when shared path exists.
10. Storage strategy when shared path does not exist.
11. Safe command construction.
12. Facility/allocation selection.
13. Job naming and traceability.
14. Preview artifact and output registration boundary.
15. Future production adapter boundary.
```

## Required Storage Strategy Answer

Explicitly document this decision:

### Shared Path Strategy

Use for:

```text
local Docker
single PVE node
same LAN workers
controlled smoke testing
```

Model:

```text
StorageRuntime or local harness prepares input under shared root.
OpenCue worker reads input path.
OpenCue worker writes output path.
Host/platform reads output path.
```

Limitations:

```text
not ideal across cloud providers
not ideal across regions
not ideal with untrusted workers
requires consistent mounts
requires path discipline
harder to scale safely
```

### Object Storage Materialization Strategy

Use for:

```text
production
cross-host execution
cross-service-provider execution
cloud workers
remote workers without shared filesystem
```

Model:

```text
StorageRuntime has input StorageReference.
Worker receives internal materialization manifest.
Worker downloads input to local scratch.
Provider runs against local scratch paths.
Worker uploads output to object storage.
Worker writes output manifest.
Platform registers StorageReference.
ProductRuntime creates/updates Product.
```

Important constraints:

```text
Signed URLs may be used internally only if necessary.
Signed URLs must not be exposed through public API.
Signed URLs must not be persisted as product identity.
Local paths must not be exposed through public API.
Object keys and storage provider internals must not leak through public API.
```

### Worker Local Scratch Strategy

Document:

```text
Worker local scratch is execution-local and disposable.
It is not Product identity.
It is not StorageReference identity.
It must be cleaned after job completion where possible.
```

### Hybrid Strategy

Document:

```text
Shared path can remain an ExecutionEnvironment-local optimization.
Object storage is the portable production abstraction.
Platform can select materialization strategy per ExecutionEnvironment.
```

## Required Proposed Types

Only add Java skeletons if they are clearly useful and low risk.

If adding skeletons, prefer docs-first and keep them non-executing.

Possible package:

```text
render-module/src/main/java/com/example/platform/render/domain/opencue/
```

Possible types:

```text
OpenCueJobSpecDraft
OpenCueLayerSpecDraft
OpenCueFrameRange
OpenCueChunkPolicy
OpenCueFacility
OpenCueAllocation
OpenCueCommandSpec
OpenCueMaterializationStrategy
OpenCueStorageStrategy
OpenCueExecutionInput
OpenCueExecutionOutput
OpenCueCjslRenderMapper
OpenCueCjslRenderMapperResult
OpenCueCjslRenderMapperIssue
```

Rules for skeletons:

```text
No Cuebot submission.
No grpcurl invocation.
No process execution.
No filesystem materialization.
No object storage calls.
No ProductRuntime calls.
No StorageRuntime calls.
No public DTO exposure.
No raw user command.
No raw filtergraph.
```

If adding Java skeletons, include unit tests for:

```text
multi-frame mapping
chunk mapping
multi-layer mapping
dependency fail-closed / stage split recommendation
shared-path materialization strategy
object-storage materialization strategy
unsafe command rejection
facility/allocation requirement
public leak prevention
```

If not adding Java skeletons, create detailed docs and examples only.

## Required Mapping Rules

Document and, if skeletons are added, encode:

### RenderExecutionPlan to OpenCue Job

```text
One RenderExecutionPlan may map to one or more OpenCue jobs.
If dependencies cannot be expressed in CJSL, split into multiple jobs/stages.
Each OpenCue job has facility, show, shot, user, job name.
Job names must include stable trace IDs but avoid unsafe characters.
```

### Logical Execution Node to Layer

```text
A homogeneous repeated operation maps to a layer.
A layer has:
  name
  type Render
  command spec
  range
  chunk
  cores
  memory
  services
```

### Repeated Work to Frames

```text
Repeated independent work maps to frames.
CUE_FRAME is used for frame identity.
Frame output paths must include frame id.
```

### Chunking

```text
Chunking is allowed only when frames are independent.
Default chunk=1.
Larger chunks require explicit bounded policy.
```

### Dependencies

```text
Do not rely on CJSL <depends>.
Use platform-side stage splitting:
  submit stage A
  wait/validate outputs
  submit stage B
```

### Failure

```text
DEAD frame maps to execution failure.
Frame exit code and logs must be captured.
Job DEAD with partial success must be represented without losing successful output metadata.
Retry policy is future work.
```

### Storage

```text
Execution path is not Product identity.
Shared path is an execution materialization detail.
Object storage is production materialization strategy.
Output must be registered through StorageRuntime in future production adapter.
```

## Required Example Artifacts

Create:

```text
docs/examples/opencue/render-execution-plan-mapping/
```

Required files:

```text
README.md
render-execution-plan-example.json
opencue-job-spec-draft-shared-path.json
opencue-job-spec-draft-object-storage.json
generated-cjsl-shared-path-example.xml
generated-cjsl-object-storage-worker-materialization-example.xml
dependency-stage-splitting-example.md
storage-strategy-comparison.md
failure-status-mapping-example.md
```

Example expectations:

```text
shared-path example:
  input and output are worker-visible shared paths

object-storage example:
  CJSL command invokes operator-controlled worker materialization wrapper
  command receives manifest path or manifest id
  no signed URL in public API
  no local host path in public API
```

Do not include real secrets, real signed URLs, or real bucket credentials.

Use placeholder IDs only.

## Required Documentation

Create:

```text
docs/review/render-execution-plan-to-cjsl-mapping-v0.md
docs/operations/opencue-render-execution-plan-mapping-runbook.md
```

Review doc required sections:

```text
1. Purpose
2. Why mapping follows P2O.0f
3. Current OpenCue validation baseline
4. What P2O.0g implements
5. What P2O.0g does not implement
6. OpenCue as ExecutionEnvironment
7. RenderExecutionPlan to job mapping
8. Logical node to layer mapping
9. Repeated work to frame mapping
10. Chunking policy
11. Multi-layer policy
12. Dependency handling without CJSL depends
13. Failure visibility mapping
14. Retry/relaunch boundary
15. Facility/allocation selection
16. Job naming and traceability
17. Shared-path storage strategy
18. Object-storage materialization strategy
19. Worker local scratch strategy
20. Hybrid strategy by ExecutionEnvironment
21. ProductRuntime boundary
22. StorageRuntime boundary
23. Public API safety boundary
24. CJSL generation boundary
25. Production OpenCue adapter boundary
26. Relationship to P2O.1 PVE smoke
27. Relationship to Artifact DAG
28. Remotion boundary
29. Follow-up tasks
```

Runbook required sections:

```text
1. Scope
2. Prerequisites
3. Inputs required from RenderExecutionPlan
4. Required ExecutionEnvironment metadata
5. Required storage strategy metadata
6. Mapping to OpenCueJobSpecDraft
7. Mapping to CJSL
8. Shared-path execution mode
9. Object-storage materialization mode
10. Dependency stage splitting
11. Failure status interpretation
12. Output registration handoff
13. Validation checklist
14. What not to do
15. Transition to P2O.1 PVE smoke
16. Transition to production adapter
```

Update concisely:

```text
docs/architecture/current/current-system-state.md
docs/architecture/blueprint/otio-render-platform-blueprint.md
docs/review/opencue-multiframe-multilayer-validation-v0.md
docs/review/cuebot-grpc-job-submission-discovery-v0.md
docs/production-safety.md
```

Required concise note:

```text
P2O.0g designs the mapping from platform RenderExecutionPlan concepts to OpenCue CJSL job/layer/frame structures after P2O.0f validated multi-frame, chunking, multi-layer, and failure visibility behavior. Because current CJSL dependency syntax is not available, RenderExecutionPlan dependencies are handled by platform-side stage splitting and submission ordering. P2O.0g also defines the storage strategy boundary: shared path for local/PVE smoke, object storage plus worker local materialization for production and cross-host execution. It does not implement production OpenCue adapter, live RenderExecutionPlan execution, StorageRuntime integration, ProductRuntime integration, public API, Remotion execution, cross-service-provider execution, or Artifact DAG.
```

## Stage 0 — Preflight

Run:

```bash
git status --short
git log --oneline -20
git diff --stat
git diff --name-only

find docs/examples/opencue -maxdepth 8 -type f | sort || true

find docs -maxdepth 8 -type f | grep -Ei "opencue|cuebot|rqd|grpc|proto|cjsl|frame|layer|dependency|renderexecutionplan|render execution plan|materialization|shared path|shared-path|object storage|storageRuntime|ProductRuntime|executionenvironment|execution environment|local|smoke|runner|ffmpeg|ffprobe|provider|binding|dsl|artifact|dag|remotion|product|storage|safety|blueprint|current-system-state" | sort || true

find render-module/src/main/java -type f | grep -Ei "opencue|renderexecutionplan|execution|cjsl|storage|product|provider|binding|render|plan" | sort || true

find render-module/src/test/java -type f | grep -Ei "opencue|renderexecutionplan|execution|cjsl|storage|product|provider|binding|render|plan" | sort || true
```

Do not begin editing until preflight is complete.

Write inventory to:

```text
/tmp/p2o0g-render-execution-plan-cjsl-inventory.md
```

## Stage 1 — Review P2O.0f Findings

Inspect:

```text
docs/review/opencue-multiframe-multilayer-validation-v0.md
docs/operations/opencue-multiframe-multilayer-runbook.md
docs/examples/opencue/local-docker-p2o0f/
docs/review/cuebot-grpc-job-submission-discovery-v0.md
```

Write:

```text
/tmp/p2o0g-opencue-findings-summary.md
```

Include:

```text
validated capabilities
blocked capabilities
CJSL constraints
runtime constraints
storage constraints
failure/status constraints
```

## Stage 2 — Design Mapping Model

Create the main review doc and example mapping files.

If Java skeletons are added, keep them non-executing and isolated.

If Java skeletons are not added, create docs/examples only.

Decision rule:

```text
Prefer docs/examples only unless existing code has a clear RenderExecutionPlan domain package that makes skeleton placement obvious.
```

## Stage 3 — Storage Strategy Design

Explicitly compare:

```text
shared path
object storage materialization
worker local scratch
hybrid per ExecutionEnvironment
```

Create:

```text
docs/examples/opencue/render-execution-plan-mapping/storage-strategy-comparison.md
```

Must answer:

```text
What if no shared path is available?
How does worker get input?
How does worker return output?
Who owns object identity?
Who owns product identity?
What must not be exposed publicly?
What is local scratch?
What should PVE use first?
What should production/cross-provider use?
```

## Stage 4 — Dependency Mapping Design

Create:

```text
docs/examples/opencue/render-execution-plan-mapping/dependency-stage-splitting-example.md
```

Must show:

```text
RenderExecutionPlan with dependency A -> B
maps to:
  OpenCue job/stage A
  wait/validate A
  OpenCue job/stage B
```

Do not use CJSL `<depends>` as the primary design.

## Stage 5 — CJSL Examples

Create example CJSL for:

```text
shared-path mode
object-storage worker-materialization mode
```

Rules:

```text
No real credentials.
No real signed URLs.
No real bucket names unless placeholder.
No local host-only paths.
No raw user input.
```

Object-storage CJSL example may use an operator-controlled wrapper command like:

```text
media-worker-materialize-and-render --manifest /mnt/opencue-shared/job/manifest.json
```

or:

```text
media-worker-materialize-and-render --manifest-id ${MANIFEST_ID}
```

But clearly document:

```text
wrapper is future worker code
not implemented in P2O.0g
not public API
not user-provided command
```

## Stage 6 — Optional Java Skeletons

Only if chosen.

If adding Java skeletons, run targeted tests.

If not adding Java skeletons, skip.

Potential command if tests exist:

```bash
./gradlew :render-module:test --tests "*OpenCue*Cjsl*"
```

## Stage 7 — Documentation Updates

Update required docs.

Make sure current-system-state says:

```text
P2O.0g is mapping design, not production adapter.
P2O.0f execution results are the basis.
Shared path is local/PVE smoke strategy.
Object storage materialization is production/cross-host strategy.
```

## Stage 8 — Safety Checks

Run:

```bash
grep -RIn "OpenCue.*Provider\|Provider.*OpenCue\|ExecutionBackend\|ProviderExtensionSPI" \
  docs/architecture docs/review docs/operations docs/examples docs/production-safety.md \
  > /tmp/p2o0g-opencue-terminology-check.log 2>&1 || true

grep -RIn "signedUrl\|signed URL\|local path\|materializedPath\|bucket\|objectKey\|storageProvider\|provider/backend\|backend/environment" \
  docs/review/render-execution-plan-to-cjsl-mapping-v0.md \
  docs/examples/opencue/render-execution-plan-mapping \
  docs/operations/opencue-render-execution-plan-mapping-runbook.md \
  docs/production-safety.md \
  > /tmp/p2o0g-storage-leak-check.log 2>&1 || true

grep -RIn "rawCommand\|filter_complex\|userCommand\|user-provided command\|plugin-inserted\|ArtifactDag\|Remotion.*render" \
  docs/review/render-execution-plan-to-cjsl-mapping-v0.md \
  docs/examples/opencue/render-execution-plan-mapping \
  docs/operations/opencue-render-execution-plan-mapping-runbook.md \
  docs/production-safety.md \
  > /tmp/p2o0g-safety-check.log 2>&1 || true

git diff | grep -Ei 'sk_live|sk_test|whsec|api[_-]?key|secret|password|token' \
  > /tmp/p2o0g-secret-check.log 2>&1 || true
```

Manual review required.

Expected:

```text
OpenCue provider terminology should be absent or explicitly corrected.
Storage terms may appear only as internal strategy/boundary discussion.
Signed URLs must be described as internal-only if mentioned.
Local paths must not be public API outputs.
Raw command/filtergraph must appear only as forbidden/boundary text.
```

## Stage 9 — Final Report

Write final report to:

```text
/tmp/claude-final-report.md
```

Use sections:

```text
Summary
Stage 0: Preflight
Stage 1: P2O.0f Findings Review
Stage 2: Mapping Model Design
Stage 3: Storage Strategy Design
Stage 4: Dependency Mapping Design
Stage 5: CJSL Examples
Stage 6: Java Skeletons
Stage 7: Documentation Updates
Stage 8: Safety Checks
Files Changed
Commands Run
Tests Run
Mapping Summary
Storage Strategy Summary
No Shared Path Strategy
Dependency Strategy Summary
CJSL Generation Boundary
Failure Mapping Summary
Production Adapter Boundary
ProductRuntime Boundary
StorageRuntime Boundary
Public API Boundary
Cross-Service-Provider Decision
Artifact DAG Boundary
Remotion Boundary
Known Limitations
Recommended Next Steps
Commit Status
```

## Final Output Rule

Do not print the full final report in chat.

At the end, print only:

```text
Report: /tmp/claude-final-report.md
Git status: output of git status --short
Commits: latest commit hash or hashes
Validation: max 10 lines
P2O.0g Status: COMPLETE / PARTIAL / BLOCKED
Mapping Design: COMPLETE / PARTIAL / BLOCKED
Shared Path Strategy: DOCUMENTED
Object Storage Materialization Strategy: DOCUMENTED
No Shared Path Strategy: DOCUMENTED
Dependency Strategy: PLATFORM_STAGE_SPLITTING / PARTIAL / BLOCKED
CJSL Generation Boundary: DOCUMENTED
Production OpenCue Adapter: NOT_IMPLEMENTED
Live Cuebot Submission: NOT_RUN
RenderExecutionPlan Integration: NOT_IMPLEMENTED
StorageRuntime Integration: NOT_IMPLEMENTED
ProductRuntime Integration: NOT_IMPLEMENTED
ProviderBindingRegistry Integration: NOT_IMPLEMENTED
Artifact DAG Integration: NOT_IMPLEMENTED / INDEFINITELY_DEFERRED
Remotion Execution: NOT_IMPLEMENTED
Public API: NOT_IMPLEMENTED
```

Never print full report body, full logs, full diffs, generated source files, generated docs, generated configs, generated CJSL specs, or generated reports in chat.

## Commit Policy

Create commits only for completed and verified stages.

Suggested commit messages:

```text
docs(architecture): design render execution plan to cjsl mapping
docs(ops): document opencue storage materialization strategies
docs(examples): add opencue render execution plan mapping examples
```

A combined commit is acceptable if changes are tightly related and checks pass.

Do not push.

Do not merge.

Do not deploy.

## Commit Candidate Files

Likely files:

```text
docs/review/render-execution-plan-to-cjsl-mapping-v0.md
docs/operations/opencue-render-execution-plan-mapping-runbook.md
docs/examples/opencue/render-execution-plan-mapping/README.md
docs/examples/opencue/render-execution-plan-mapping/render-execution-plan-example.json
docs/examples/opencue/render-execution-plan-mapping/opencue-job-spec-draft-shared-path.json
docs/examples/opencue/render-execution-plan-mapping/opencue-job-spec-draft-object-storage.json
docs/examples/opencue/render-execution-plan-mapping/generated-cjsl-shared-path-example.xml
docs/examples/opencue/render-execution-plan-mapping/generated-cjsl-object-storage-worker-materialization-example.xml
docs/examples/opencue/render-execution-plan-mapping/dependency-stage-splitting-example.md
docs/examples/opencue/render-execution-plan-mapping/storage-strategy-comparison.md
docs/examples/opencue/render-execution-plan-mapping/failure-status-mapping-example.md
docs/architecture/current/current-system-state.md
docs/architecture/blueprint/otio-render-platform-blueprint.md
docs/review/opencue-multiframe-multilayer-validation-v0.md
docs/review/cuebot-grpc-job-submission-discovery-v0.md
docs/production-safety.md
```

Adjust based on actual `git status --short`.

Do not commit `AGENT_TASK.md` unless it is intentionally tracked project documentation. Usually it should be discarded:

```bash
git checkout -- AGENT_TASK.md
```
# Preview deployment baseline
