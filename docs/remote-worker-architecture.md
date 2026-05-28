# Remote Render Worker Architecture

> **Last updated**: 2026-05-13
> **Module**: `remote-render-worker`

## Overview

The remote render worker enables distributed video rendering across multiple nodes. Workers register with the main API server, accept render jobs, execute them asynchronously, and report status back.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Server (platform-app)                    │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │ RenderJob    │  │ ExportPolicy    │  │ RenderProvider   │   │
│  │ Controller   │  │ Service         │  │ Router           │   │
│  └──────────────┘  └─────────────────┘  └──────────────────┘   │
│         │                                       │               │
│         │         ┌─────────────────┐           │               │
│         └────────▶│ WorkerRegistry  │◀──────────┘               │
│                   │ Service         │                           │
│                   └────────┬────────┘                           │
│                            │                                    │
└────────────────────────────┼────────────────────────────────────┘
                             │ HTTP / REST
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼───┐  ┌──────▼────┐  ┌──────▼────────┐
     │ Worker #1  │  │ Worker #2 │  │ Worker #N     │
     │ (GPU)      │  │ (CPU)     │  │ (GPU+CPU)     │
     │ :8090      │  │ :8091     │  │ :809N         │
     └────────────┘  └───────────┘  └───────────────┘
```

## Components

### WorkerRegistryService

Manages worker registration, heartbeats, and status tracking.

```java
// Register a worker
String workerId = registry.registerWorker("http://gpu-node:8090", 4);

// Update status
registry.updateWorkerStatus(workerId, WorkerStatus.BUSY);

// Heartbeat
registry.heartbeat(workerId);

// Query workers
Map<String, WorkerInfo> workers = registry.getAllWorkers();
```

### RemoteRenderService

Executes render jobs on the remote worker using JavaCV.

```java
// Submit job
RemoteRenderJob job = service.submitJob(workerId, "default_1080p", timelineJson);

// Check status
RemoteRenderJob status = service.getJobStatus(job.jobId());

// Cancel
RemoteRenderJob cancelled = service.cancelJob(job.jobId());
```

### RemoteWorkerController

REST API for worker operations.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/remote-worker/register` | POST | Register new worker |
| `/api/v1/remote-worker/deregister/{id}` | POST | Deregister worker |
| `/api/v1/remote-worker/heartbeat/{id}` | POST | Worker heartbeat |
| `/api/v1/remote-worker/workers` | GET | List all workers |
| `/api/v1/remote-worker/workers/{id}` | GET | Get worker status |
| `/api/v1/remote-worker/workers/{id}/jobs` | POST | Submit render job |
| `/api/v1/remote-worker/jobs/{id}` | GET | Get job status |
| `/api/v1/remote-worker/jobs/{id}/cancel` | POST | Cancel job |

## Worker Lifecycle

```
REGISTER → IDLE → BUSY → IDLE → ... → DEREGISTER
              ↓              ↓
           HEARTBEAT     HEARTBEAT
              ↓              ↓
           TIMEOUT → OFFLINE → DEREGISTER
```

## Job Lifecycle

```
SUBMITTED → RUNNING → COMPLETED
                      ↓
                    FAILED
                      ↓
                    CANCELLED
```

## Remote Worker Module Structure

```
remote-render-worker/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/example/platform/remoterender/
│   │   │   ├── RemoteRenderWorkerApplication.java
│   │   │   ├── api/
│   │   │   │   └── RemoteWorkerController.java
│   │   │   ├── app/
│   │   │   │   ├── RemoteRenderService.java
│   │   │   │   └── WorkerRegistryService.java
│   │   │   ├── domain/
│   │   │   │   ├── RemoteRenderJob.java
│   │   │   │   └── WorkerStatus.java
│   │   │   └── config/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/example/platform/remoterender/
│           └── RemoteRenderWorkerTest.java
```

## Deployment

### Docker Compose (Multi-Worker)

```yaml
services:
  api:
    build: .
    ports: ["8080:8080"]

  render-worker-cpu-1:
    build:
      context: .
      dockerfile: remote-render-worker/Dockerfile
    ports: ["8090:8090"]
    environment:
      - WORKER_ADDRESS=http://render-worker-cpu-1:8090
      - MAX_CONCURRENT_JOBS=4

  render-worker-gpu-1:
    build:
      context: .
      dockerfile: remote-render-worker/Dockerfile.gpu
    ports: ["8091:8090"]
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    environment:
      - WORKER_ADDRESS=http://render-worker-gpu-1:8090
      - MAX_CONCURRENT_JOBS=2
      - GPU_ENABLED=true
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: render-worker-gpu
spec:
  replicas: 2
  template:
    spec:
      containers:
         - name: worker
           # NOTE: Use explicit image tag (Git SHA or semver), never :latest in production.
           # Example: registry.example.com/platform-render-worker:2025.05.25-abc1234
           image: platform-render-worker:dev
          resources:
            limits:
              nvidia.com/gpu: 1
          env:
            - name: MAX_CONCURRENT_JOBS
              value: "2"
```

## OTIO Timeline Metadata Flow

```
Frontend Timeline → OTIO JSON → RenderJob.timelineJson()
                                        ↓
                              RemoteRenderJob.timelineJson()
                                        ↓
                              RemoteRenderService.parseTimeline()
                                        ↓
                              JavaCVRenderService.renderWithSubtitleBurnIn()
```

## Error Handling

All exceptions use configured error codes:

| Code | Message | Trigger |
|------|---------|---------|
| `RENDER-500-001` | Remote render failed | General failure |
| `RENDER-404-001` | Render job not found | Invalid job ID |
| `RENDER-404-004` | Worker not found | Invalid worker ID |

## Frontend Integration

The Export Panel shows worker status:

- **Local/Remote toggle** - Switch between local and remote rendering
- **Worker list** - Shows registered workers with status indicators
- **GPU indicator** - Shows when GPU-accelerated preset is selected
- **Status colors** - Green (IDLE), Yellow (BUSY), Gray (OFFLINE), Red (ERROR)

## Testing

```bash
# Run remote worker tests
./gradlew :remote-render-worker:test

# Run all tests
./gradlew test
```

---

*This document reflects the remote worker architecture as of 2026-05-13.*