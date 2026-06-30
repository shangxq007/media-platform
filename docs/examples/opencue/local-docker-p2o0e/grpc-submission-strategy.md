# gRPC Submission Strategy — Local Docker OpenCue

Date: 2026-06-30
Task: P2O.0e
Status: Strategy locked, spike complete

---

## Chosen Approach

**grpcurl with proto files from host.**

All gRPC calls to Cuebot are made from the host machine using `grpcurl`, with proto files downloaded from the OpenCue GitHub repository and passed via `-import-path` and `-proto` flags.

```
grpcurl -plaintext \
  -import-path /tmp/opencue-protos \
  -proto job.proto \
  -d '<json payload>' \
  localhost:8443 \
  job.JobInterface/SubmitJob
```

This is the canonical submission path for the local Docker environment.

---

## Why This Approach Was Chosen

This is the **only viable path**. The decision was forced by elimination of all alternatives.

### Constraint: No server-side gRPC reflection

Cuebot does not enable `grpc.reflection.v1alpha.ServerReflection` in its Docker image. `grpcurl` cannot discover services or message schemas without explicit proto files.

### Constraint: No tools inside containers

The Cuebot container contains only the JAR file and a JVM (embedded in the JAR launch). There is no `python`, `pip`, `java` CLI, `cueadmin`, `cue`, or any other tool available inside the container for interactive use.

### Constraint: No Python client available

The `opencue` Python package is not published on PyPI for the version running in the local Docker setup. The upstream repository contains Python client code, but it requires building from source with dependencies that are not trivially available.

### Constraint: No Java CLI available

While the Cuebot container runs a Java application, the JRE is embedded and not exposed as a CLI tool. There is no `java -jar` path available from within the container for ad-hoc job submission.

**Result:** grpcurl on host with local proto files is the only path that works without modifying the containers or building additional tooling.

---

## Alternatives Rejected

| Alternative                  | Rejected Because                                        |
|------------------------------|---------------------------------------------------------|
| Python `opencue` client      | Not on PyPI for local version; requires source build    |
| Container CLI (`cueadmin`)   | Not installed in Docker image                           |
| Container CLI (`cue`)        | Not installed in Docker image                           |
| Java CLI from container      | No standalone JRE/JDK exposed; JAR is app-only          |
| gRPC reflection              | Not enabled in Cuebot Docker image                      |
| REST API                     | Cuebot exposes gRPC only; no REST gateway in local env  |
| Direct database insertion    | Violates platform boundaries; Cuebot owns job state     |

---

## What Is Verified

The following have been validated through live testing in the local Docker environment:

| Claim                              | Evidence                                      |
|------------------------------------|-----------------------------------------------|
| gRPC API is reachable              | `GetJobs` returns empty list, no errors       |
| XML spec parsing works             | `SubmitJob` accepts CJSL XML, returns job ID  |
| Job submission creates DB records  | Job visible in `GetJob` response after submit |
| RQD executes frames                | RQD logs show command execution, exit code 0  |
| Frame completion propagates        | Job status transitions to `FINISHED`          |
| Show/Facility/Allocation exist     | `GetShows`, `GetFacility` return valid objects |
| Proto files cover full API surface | 20 protos covering all 15+ service interfaces |
| Flyway V11 migration applied       | GPU columns exist, no SQL errors on startup   |

---

## What Is Not Verified

The following remain untested and are out of scope for P2O.0e:

| Item                           | Risk   | Reason Out of Scope                    |
|--------------------------------|--------|----------------------------------------|
| Production load / throughput   | Medium | Single-frame smoke only                |
| Multi-frame jobs (10+ frames)  | Low    | Only 1-frame jobs submitted            |
| Frame dependencies             | Medium | No depend chains tested                |
| Complex CJSL specs             | Medium | Only echo commands; no real render     |
| Job preemption / retry         | Medium | Not triggered in smoke                 |
| Layer-level resource limits    | Low    | No CPU/memory/GPU limits specified     |
| Show-level quotas              | Low    | Single show, single user               |
| TLS / mTLS to Cuebot           | Low    | Plaintext only in local Docker         |
| Concurrent submissions         | Medium | Single submission at a time            |
| Long-running frames (>60s)     | Low    | All frames complete in <1s             |

---

## Next Action

**P2O.0f — Comprehensive Submission Testing**

P2O.0f should cover:

1. Multi-frame jobs (10, 50, 100 frames) to validate RQD dispatch at scale.
2. Frame dependencies (frame N depends on frame N-1) to validate the depend system.
3. Layer types beyond RENDER (PREPROCESS, POSTPROCESS, UTIL).
4. Job retry and failure handling (intentional non-zero exit codes).
5. Resource specification in CJSL (CPU, memory, GPU hints).
6. Concurrent job submission (2-3 jobs in parallel).

The grpcurl + proto files approach is stable and should be carried forward as the submission mechanism for P2O.0f.

---

*End of strategy document.*
