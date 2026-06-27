---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Capability C8 — OpenCue Execution Environment (Phase 1)

## Implemented

| Component | Role |
|-----------|------|
| `OpenCueJobSpec` | Platform model: jobName, owner, priority, tags, environmentVariables, resourceRequirements, layers[] |
| `OpenCueJobSpec.OpenCueLayerSpec` | layerName, commands[], frameCount, frameEnvironment |
| `OpenCueProperties` | Configuration: server, grpcPort, timeouts, maxLayers, maxCommandsPerLayer, maxEnvVars, maxTags, priority bounds, allowNetworkSubmit (false), stubModeEnabled (true), productionSubmitEnabled (false), enabled (false) |
| `OpenCueExecutionEnvironment` | Implements `ExecutionEnvironment` — envId="opencue", submit/cancel/status (Phase 1 stub), disabled-by-default guard, lifecycle status mapping |
| `OpenCueEnvironmentCompiler` | Implements `EnvironmentCompiler` — compiles BackendExecutionSpec → ExecutionJob, deterministic compilation |
| `OpenCueJobSpecValidator` | Internal validator — fails closed on null/blank job name, invalid priority, empty layers, blank layer name, empty commands, shell injection, path traversal, remote URLs, forbidden env vars |

## R1 Hardening Status (COMPLETED 2026-06-27)

- `OpenCueProperties` hardened with safe defaults (all network/code/production toggles fail-closed)
- `OpenCueJobSpecValidator` validates job spec before submit (shell injection, path traversal, forbidden env vars)
- `OpenCueExecutionEnvironment` refuses submit when disabled, rejects non-stub production submit unless explicitly enabled
- `OpenCueEnvironmentCompiler` produces deterministic ExecutionJob for equivalent BackendExecutionSpec
- Lifecycle state mapping (OpenCue → ExecutionStatus) verified for all defined states + null/unknown → FAILED
- Architecture boundary tests: OpenCue is ExecutionEnvironment, not Backend or RenderProvider; does not bypass ExecutionControlService; does not require SPI changes
- OpenCue remains disabled by default; no real REST/gRPC client

## R1 Configuration

```yaml
opencue:
  server: localhost
  grpc-port: 8443
  timeout-sec: 300
  submit-timeout-sec: 60
  status-timeout-sec: 30
  cancel-timeout-sec: 30
  enabled: false
  max-layers: 128
  max-commands-per-layer: 256
  max-environment-variables: 64
  max-tags: 32
  min-priority: 1
  max-priority: 999
  default-owner: platform
  default-priority: 50
  allow-network-submit: false
  stub-mode-enabled: true
  production-submit-enabled: false
```

## Lifecycle Mapping

| OpenCue State | ExecutionStatus | Notes |
|---------------|-----------------|-------|
| pending | SUBMITTED | Job submitted to OpenCue |
| queued | QUEUED | Waiting for dispatch |
| running | RUNNING | Actively processing |
| succeeded | COMPLETED | Finished successfully |
| dead | FAILED | Finished with error |
| killed | CANCELLED | Explicitly stopped |
| dependent | QUEUED | Waiting on dependency |
| null/blank/unknown | FAILED | Fail-closed |

Platform owns lifecycle semantics. OpenCue reports state; platform translates.

## Architecture Boundary Compliance

- OpenCue is ExecutionEnvironment, not ExecutionBackend
- OpenCueEnvironmentCompiler is EnvironmentCompiler
- No Kernel/SPI/Product/Timeline/Execution lifecycle changes required
- Does not access repositories, ProductRuntime, StorageRuntime, planning, or governance services
- Does not bypass ExecutionControlService

## Tests

R1 added: `OpenCuePropertiesTest` (14), `OpenCueJobSpecValidatorTest` (16), `OpenCueEnvironmentTest` (34).

## Deferred (Phase 2+)

| Item |
|------|
| OpenCue REST/gRPC client (real submit behind explicit config) |
| Frame-level dispatch (multi-frame layers) |
| Worker registration + heartbeat |
| Retry/lease integration |
| BMF graph → OpenCue layer mapping |
| Integration-test profile for real OpenCue cluster |
