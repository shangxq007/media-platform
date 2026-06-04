# GPU Acceleration for Render Pipeline

> **Last updated**: 2026-05-13
> **Module**: `render-module`

## Overview

The render pipeline supports GPU-accelerated video encoding through JavaCV's FFmpeg JNI bindings. GPU presets enable hardware-encoding via NVIDIA NVENC, Intel/AMD VAAPI, and other hardware acceleration APIs.

## GPU Presets

| Preset | Codec | GPU API | Resolution | Bitrate |
|--------|-------|---------|------------|---------|
| `gpu_h264` | H.264 NVENC | NVIDIA | 1920x1080 | 8000k |
| `gpu_h265` | H.265 NVENC | NVIDIA | 1920x1080 | 6000k |
| `gpu_vp9` | VP9 VAAPI | Intel/AMD | 1920x1080 | 6000k |

## Architecture

```
RenderJob → ProviderRouter → JavaCVRenderService (GPU preset)
                                   ↓
                            FFmpegFrameRecorder
                                   ↓
                            NVENC/VAAPI/QSV (via JNI)
                                   ↓
                            GPU Hardware Encoder
                                   ↓
                            H.264/H.265/VP9 Output
```

## Configuration

### Application Properties

```yaml
app:
  render:
    gpu:
      enabled: true
      device: 0              # GPU device index
      codec: nvenc           # nvenc, vaapi, qsv
      preset: p4             # NVENC preset: p1(fast) to p7(slow)
      tune: hq               # hq, ll, ull, lossless
      rc: vbr                # vbr, cbr, cq
```

### Render Preset Selection

GPU presets are selected via the profile string:

```java
// GPU H.264 via NVENC
RenderPreset preset = RenderPreset.fromProfile("gpu_h264");

// GPU H.265 via NVENC
RenderPreset preset = RenderPreset.fromProfile("gpu_h265");

// GPU VP9 via VAAPI
RenderPreset preset = RenderPreset.fromProfile("gpu_vp9");
```

## Docker GPU Configuration

### NVIDIA GPU (nvidia-docker)

```dockerfile
FROM nvidia/cuda:12.0-devel-ubuntu22.04
# Install JavaCV + FFmpeg with NVENC support
RUN apt-get install -y ffmpeg libnvidia-encode-535
COPY app.jar /app/
```

```yaml
# docker-compose.yml
services:
  render-worker-gpu:
    build: .
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    environment:
      - NVIDIA_VISIBLE_DEVICES=all
      - NVIDIA_DRIVER_CAPABILITIES=compute,video,utility
```

### Intel/AMD GPU (VAAPI)

```dockerfile
FROM ubuntu:22.04
RUN apt-get install -y ffmpeg intel-media-va-driver
# Enable VAAPI device access
```

```yaml
# docker-compose.yml
services:
  render-worker-gpu:
    devices:
      - /dev/dri:/dev/dri
    group_add:
      - video
```

## Encoder Options

### NVIDIA NVENC

| Option | Values | Description |
|--------|--------|-------------|
| `preset` | p1-p7 | Speed/quality tradeoff (p4=balanced) |
| `tune` | hq, ll, ull, lossless | Quality tuning |
| `rc` | vbr, cbr, cq | Rate control mode |
| `cq` | 0-51 | Quality level (lower=better) |
| `bf` | 0-4 | B-frame count |
| `g` | int | GOP size |

### Intel/AMD VAAPI

| Option | Values | Description |
|--------|--------|-------------|
| `quality` | balanced, speed, quality | Quality mode |
| `rate_control` | CBR, VBR, CQP | Rate control |

## Performance Comparison

| Mode | 1080p30 H.264 | CPU Usage | Speed |
|------|---------------|-----------|-------|
| CPU (libx264) | ~8000kbps | 100% | 1x |
| GPU NVENC | ~8000kbps | 30% | 3-5x |
| GPU NVENC (HQ) | ~12000kbps | 35% | 2-4x |

## Error Handling

| Error Code | Message | Trigger |
|------------|---------|---------|
| `RENDER-500-006` | GPU encoder not available | No GPU device found |
| `RENDER-500-007` | GPU encoder initialization failed | Driver/codec mismatch |
| `RENDER-500-008` | GPU encoding error | Hardware encoding failure |

## Fallback Strategy

When GPU encoding fails, the system falls back to CPU encoding:

```java
try {
    renderService.render(jobId, inputPath, outputPath, gpuPreset);
} catch (Exception e) {
    log.warn("GPU encoding failed, falling back to CPU: {}", e.getMessage());
    RenderPreset cpuPreset = RenderPreset.fromProfile("default_1080p");
    renderService.render(jobId, inputPath, outputPath, cpuPreset);
}
```

## Testing

```bash
# Run GPU-related tests
./gradlew :render-module:test --tests "JavaCVRenderServiceTest"

# Docker GPU test
docker compose -f docker-compose.gpu.yml up render-worker-gpu
```

---

*This document reflects GPU acceleration support as of 2026-05-13.*