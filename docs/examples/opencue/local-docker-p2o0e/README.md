# P2O.0e — Cuebot gRPC Job Submission Discovery and Spike

## Purpose

P2O.0e resolves the P2O.0d limitation: no true OpenCue job was submitted through Cuebot.
P2O.0d used docker exec fallback. P2O.0e discovers the Cuebot gRPC submission surface
and builds a minimal operator-run job submission spike.

## Status

COMPLETE_TRUE_SUBMISSION_SPIKE

## What This Implements

- gRPC submission API discovery via proto files
- Database schema migration (V11 GPU columns) for Cuebot 1.19.1 compatibility
- True job submission via grpcurl + CJSL XML spec
- Smoke level 0: shared-path probe (echo command)
- Smoke level 1: ffmpeg video generation
- Smoke level 2: ffmpeg caption overlay

## What This Does Not Implement

- Production OpenCue adapter
- RenderExecutionPlan integration
- ProductRuntime / StorageRuntime
- Public API endpoints
- Cross-service-provider execution
- Artifact DAG
- Remotion execution

## Architecture

OpenCue = ExecutionEnvironment (not Provider, not ExecutionBackend)

## Prerequisites

- Docker and Docker Compose
- grpcurl binary at /tmp/grpcurl
- Proto files at /tmp/opencue-protos/
- P2O.0c runtime (PostgreSQL, Cuebot, RQD)
- V11 GPU column migration applied

## Quick Start

```bash
# Verify runtime
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-runtime-health.sh

# Inspect gRPC surface
bash docs/examples/opencue/local-docker-p2o0e/scripts/inspect-cuebot-grpc.sh

# Run smoke level 0
bash docs/examples/opencue/local-docker-p2o0e/scripts/prepare-grpc-submission-smoke.sh
bash docs/examples/opencue/local-docker-p2o0e/scripts/submit-grpc-smoke-level-0.sh
bash docs/examples/opencue/local-docker-p2o0e/scripts/validate-grpc-submission-output.sh
bash docs/examples/opencue/local-docker-p2o0e/scripts/copy-preview-artifacts.sh
```

## Files

```
docs/examples/opencue/local-docker-p2o0e/
├── README.md
├── job-submission-discovery.md
├── grpc-submission-strategy.md
├── shared-path-layout.txt
└── scripts/
    ├── inspect-cuebot-grpc.sh
    ├── prepare-grpc-submission-smoke.sh
    ├── submit-grpc-smoke-level-0.sh
    ├── submit-grpc-smoke-level-1.sh
    ├── submit-grpc-smoke-level-2.sh
    ├── validate-grpc-submission-output.sh
    ├── copy-preview-artifacts.sh
    └── collect-grpc-submission-logs.sh
```

## Key Findings

1. gRPC reflection NOT enabled on Cuebot port 8443
2. Proto files required for grpcurl -import-path
3. Submission API: job.JobInterface/LaunchSpecAndWait
4. Spec format: CJSL XML (cjsl-1.15.dtd)
5. Root element: <spec> (not <spcue>)
6. Facility must match host allocation for dispatch
7. V11 GPU column migration required for Cuebot 1.19.1

## Preview Artifacts

```
build/opencue-shared/media-platform-smoke/preview/p2o0e/
├── smoke-level-0/
│   ├── shared-path-probe.txt
│   └── opencue-job-summary.txt
├── smoke-level-1/
│   ├── output.mp4
│   ├── ffmpeg.stderr.log
│   ├── ffprobe-output.txt
│   └── opencue-job-summary.txt
└── smoke-level-2/
    ├── output.mp4
    ├── ffmpeg.stderr.log
    ├── ffprobe-output.txt
    └── opencue-job-summary.txt
```
