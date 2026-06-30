# Review: Cuebot gRPC Job Submission Discovery v0

## 1. Purpose

P2O.0e investigates Cuebot gRPC job submission after P2O.0d fallback execution.
It discovers available submission tooling, proto/API surfaces, and minimal client
options for true Cuebot-submitted jobs.

## 2. Why Cuebot gRPC Discovery Follows P2O.0d

P2O.0d validated worker-side execution through docker exec fallback.
Smoke Level 0/1/2 all passed. Preview artifacts were saved locally.
But no true OpenCue job was submitted through Cuebot.
Cuebot exposes gRPC on port 8443.
No cueadmin/cuesubmit/cuecmd were available in containers.
No Python OpenCue client was available in containers.

## 3. Current P2O.0d Fallback Baseline

P2O.0d Status: PARTIAL_JOB_SUBMISSION_READY
Docker Runtime: PASS
Cuebot: PASS
RQD: PASS
RQD Registered: PASS
OpenCue Job Submission: PARTIAL / FALLBACK_ONLY
Smoke Level 0-2: PASS via docker exec fallback
Preview Artifacts: PASS

## 4. What P2O.0e Implements

- gRPC submission API discovery via proto files
- Database schema migration for Cuebot 1.19.1 compatibility
- True job submission via grpcurl + CJSL XML spec
- Smoke level 0/1/2 via true Cuebot submission
- Preview artifact collection

## 5. What P2O.0e Does Not Implement

- Production OpenCue adapter
- RenderExecutionPlan integration
- ProductRuntime / StorageRuntime
- Public API endpoints
- Cross-service-provider execution
- Artifact DAG
- Remotion execution

## 6. OpenCue as ExecutionEnvironment

OpenCue remains ExecutionEnvironment, not Provider or ExecutionBackend.
OpenCue does not own visual capability semantics.
OpenCue does not replace ProviderBindingPlan.
OpenCue does not require Artifact DAG for smoke validation.

## 7. Runtime Readiness Recap

P2O.0c validated:
- PostgreSQL: PASS
- Cuebot: PASS
- RQD: PASS
- RQD Registered: PASS
- Shared Path Mount: PASS
- RQD ffmpeg: PASS
- RQD ffprobe: PASS
- Runtime Startup: PASS

P2O.0e required V11 GPU column migration for RQD registration.

## 8. Cuebot gRPC Surface Discovery

gRPC reflection: NOT ENABLED
Proto files: 20 files from OpenCue GitHub
Key services: job.JobInterface, show.ShowInterface, facility.FacilityInterface, host.HostInterface

## 9. Proto/API Discovery

Submission API: job.JobInterface/LaunchSpecAndWait
Request: JobLaunchSpecAndWaitRequest { string spec = 1; }
Response: JobLaunchSpecAndWaitResponse { JobSeq jobs = 1; }
Spec format: CJSL XML (cjsl-1.15.dtd)

## 10. Submission Method Candidates

- Option A: OpenCue CLI — NOT AVAILABLE in containers
- Option B: Python OpenCue Client — NOT AVAILABLE (not on PyPI)
- Option C: grpcurl with proto files — CHOSEN (only viable path)
- Option D: Java client — NOT AVAILABLE (no runtime in container)

## 11. Minimal Job Model Requirements

Required: show, shot, user, job name, layer name+type, cmd, range, chunk
Optional: facility, paused, cores, memory, services

## 12. Facility/Alloc/Show Requirements

Show: "testing" (seed)
Facility: "local" (seed)
Allocation: "local.general" (seed, default)
Job facility must match host allocation facility for dispatch.

## 13. Minimal Client Spike Strategy

grpcurl from host with proto files.
Build CJSL XML spec manually.
Submit via LaunchSpecAndWait.
Poll database for frame completion.
Copy output from Docker volume to host.

## 14. True Submission Smoke Result

All 3 smoke levels PASSED:
- Level 0: shared-path probe → SUCCEEDED (exit 0)
- Level 1: ffmpeg video generation → SUCCEEDED (exit 0)
- Level 2: ffmpeg caption overlay → SUCCEEDED (exit 0)

## 15. Preview Artifact Model

Preview root: build/opencue-shared/media-platform-smoke/preview/p2o0e/
Each smoke level has its own subdirectory with output files and job summaries.

## 16. Safety Model

- No secrets in code
- No production credentials
- No public API exposure
- No signed URLs or filesystem paths in API responses
- OpenCue terminology correct (ExecutionEnvironment, not Provider)
- No cross-service-provider execution

## 17. Cross-Service-Provider Decision

P2O.0e does not implement cross-service-provider execution.
OpenCue is ExecutionEnvironment only.

## 18. Relationship to P2O.0f

P2O.0f can build on P2O.0e for more comprehensive submission testing:
- Multi-frame jobs
- Job dependencies
- Complex specs
- Error handling

## 19. Relationship to P2O.1 PVE Smoke

P2O.1 PVE smoke can use P2O.0e submission infrastructure.

## 20. Relationship to RenderExecutionPlan

P2O.0e does not implement RenderExecutionPlan integration.
RenderExecutionPlan can use P2O.0e submission path when implemented.

## 21. Relationship to Production OpenCue Adapter

P2O.0e is a spike, not a production adapter.
Production adapter would need:
- Connection pooling
- Error handling
- Retry logic
- Job monitoring
- Frame status tracking

## 22. Relationship to ProductRuntime / StorageRuntime

P2O.0e does not call ProductRuntime or StorageRuntime.
These are separate concerns.

## 23. Artifact DAG Boundary

P2O.0e does not implement Artifact DAG.
Artifact DAG is indefinitely deferred (ADR-025).

## 24. Remotion Boundary

P2O.0e does not use Remotion.
Remotion is a separate execution path.

## 25. Follow-Up Tasks

- P2O.0f: More comprehensive submission testing
- P2O.1: PVE smoke validation
- Future: Production OpenCue adapter (separate task)
- Future: RenderExecutionPlan integration (separate task)
