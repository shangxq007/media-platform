# Render Worker Deployment Guide

> **Generated**: 2026-05-08T15:13Z
> **Status**: Initial deployment documentation.

---

## Deployment Architecture

The media platform is designed for a split deployment model:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                           │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    platform-app (2+ replicas)              │  │
│  │  - REST API, job submission, artifact catalog              │  │
│  │  - No media binaries required                              │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 render-worker (2+ replicas)                 │  │
│  │  - FFmpeg, FFprobe                                         │  │
│  │  - MLT/melt                                                │  │
│  │  - High CPU, optional GPU                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              packaging-worker (1+ replicas)                 │  │
│  │  - GPAC/MP4Box                                             │  │
│  │  - Moderate CPU                                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              scheduler/outbox-worker (1+ replicas)          │  │
│  │  - Job scheduling, outbox processing                       │  │
│  │  - Low CPU                                                 │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              ai-worker (1+ replicas, optional)              │  │
│  │  - AI script generation                                    │  │
│  │  - GPU recommended                                         │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Binary Dependencies

### Render Worker

| Tool | Purpose | Minimum Version |
|------|---------|----------------|
| FFmpeg | Video encoding/transcoding | 5.0+ |
| FFprobe | Media probing | 5.0+ |
| MLT/melt | Timeline rendering | 7.0+ |

### Packaging Worker

| Tool | Purpose | Minimum Version |
|------|---------|----------------|
| GPAC/MP4Box | DASH/HLS packaging | 2.0+ |

## System Requirements

### Render Worker

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU cores | 8 | 16+ |
| RAM | 16 GB | 32+ GB |
| Temp storage | 50 GB SSD | 200+ GB SSD |
| GPU | Optional | NVIDIA with NVENC |

### Packaging Worker

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU cores | 4 | 8+ |
| RAM | 8 GB | 16 GB |
| Temp storage | 20 GB SSD | 100 GB SSD |

## Fonts and Codecs

### Fonts (Render Worker)
- DejaVu Sans (for subtitle burn-in)
- Liberation Sans (fallback)

### Codecs (Render Worker)
- H.264 (libx264) — required
- H.265 (libx265) — recommended
- AAC — required
- MP3 — optional

## Worker Image Strategy

### Option A: Single Media Image
One Docker image with all media tools (ffmpeg, melt, MP4Box).

**Pros:** Simpler deployment
**Cons:** Larger image (~2-3 GB), all tools on all workers

### Option B: Specialized Images
Separate images for render-worker and packaging-worker.

**Pros:** Smaller images, independent scaling
**Cons:** More images to maintain

**Recommended:** Option B for production.

## Temp Workspace

Each worker needs a writable temp directory for intermediate files:

```yaml
# Kubernetes volume mount
volumeMounts:
  - name: temp-workspace
    mountPath: /tmp/render

volumes:
  - name: temp-workspace
    emptyDir:
      sizeLimit: 50Gi
      medium: Memory  # Use tmpfs for faster I/O (if RAM permits)
```

## Object Storage

Workers need access to object storage for input/output:

```yaml
# Example: S3-compatible storage
storage:
  endpoint: https://s3.amazonaws.com
  bucket: media-platform-artifacts
  access-key: ${AWS_ACCESS_KEY_ID}
  secret-key: ${AWS_SECRET_ACCESS_KEY}
```

## Concurrency and Timeouts

```yaml
render:
  worker:
    max-concurrent-jobs: 4
    default-timeout-minutes: 60
    max-timeout-minutes: 240
  packaging:
    max-concurrent-jobs: 8
    default-timeout-minutes: 15
```

## Sandbox Policy

```yaml
render:
  sandbox:
    enabled: true
    allowed-output-paths:
      - /tmp/render/
      - /output/
    network-access: false  # Workers should not access external network
    max-output-bytes: 67108864  # 64MB
```

## Observability

### Metrics
- `render.jobs.total` — total render jobs
- `render.jobs.failed` — failed render jobs
- `render.duration.seconds` — render duration histogram
- `render.queue.depth` — pending jobs in queue

### Logging
- Tool execution stdout/stderr captured in `ToolExecutionLog`
- Structured logging with job ID correlation

### Health Checks
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  health:
    render:
      enabled: true
```

---

*See also: [render-provider-integration.md](render-provider-integration.md), [deployment-resource-requirements.md](deployment-resource-requirements.md)*
