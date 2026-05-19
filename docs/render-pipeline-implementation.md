# Render Pipeline Implementation

> **Last updated**: 2026-05-12
> **Status**: Active implementation with JavaCV
> **Module**: `render-module`

## Current Implementation Overview

The render pipeline is a fully functional system for video rendering and transcoding using JavaCV (Java bindings for FFmpeg). It supports multi-track timeline processing, AI script generation, quota management, and artifact storage.

## Architecture Components

### 1. RenderJob Lifecycle

```
QUEUED → AI_PROCESSING → RENDERING → COMPLETED
         ↓               ↓          ↓
      REJECTED        FAILED   CANCELLED
```

**State Transitions:**
- `QUEUED`: Job created and waiting for AI script generation
- `AI_PROCESSING`: AI gateway generates script from prompt
- `RENDERING`: JavaCV processes the video
- `COMPLETED`: Artifact stored and registered
- `FAILED`: Any step encounters an error
- `REJECTED`: Quota exceeded or validation failed
- `CANCELLED`: User-initiated cancellation

### 2. Component Responsibilities

#### `RenderController` (`/api/v1/render/*`)
- REST endpoints for job management
- Handles job creation, retrieval, cancellation, retry
- Supports both tenant-scoped and legacy endpoints

#### `RenderJobService`
- Core job orchestration logic
- State machine validation
- Tenant access control
- Status history tracking

#### `RenderOrchestratorService`
- Full workflow orchestration
- Quota checking and consumption
- AI script generation via `AiGatewayPort`
- Provider routing and execution
- Storage integration

#### `JavaCVRenderProvider`
- **Primary render implementation**
- Uses JavaCV/FFmpeg for video processing
- Supports clipping, transcoding, subtitles, watermarks
- Handles OTIO timeline format
- Generates placeholder videos for empty timelines

### 3. RenderProvider Router

The `RenderProviderRouter` selects the appropriate provider based on profile:

```java
// Current implementation: always returns JavaCVRenderProvider
// Future: will route based on profile, tier, capabilities
RenderProvider route(String profile) {
    return javaCVRenderProvider; // Simplified for now
}
```

## JavaCV Implementation Details

### Supported Capabilities

**Formats:** MP4, OGG, WebM, MOV

**Codecs:** H.264, AAC, MP3, VP9

**Effects:**
- Transitions: fade_in, fade_out, cross_dissolve, dissolve
- Video: blur, sharpen, brightness, contrast, grayscale, sepia
- Text: subtitle_burn_in
- Audio: volume
- Overlay: watermark

**Profiles:**
- `default_1080p`, `default_720p`
- `social_1080p`, `social_720p`
- `mobile_480p`, `4k_2160p`
- `free_720p_watermarked`, `pro_1080p`, `team_4k`

### Processing Flow

```mermaid
graph TD
    A[SubmitRenderJobRequest] --> B[Quota Check]
    B --> C{Quota OK?}
    C -->|No| D[REJECTED]
    C -->|Yes| E[Create Job (QUEUED)]
    E --> F[AI Script Generation]
    F --> G[AI_PROCESSING]
    G --> H{Script Generated?}
    H -->|No| I[FAILED]
    H -->|Yes| J[Route Provider]
    J --> K[JavaCVRenderProvider.render()]
    K --> L[RENDERING]
    L --> M{Render Success?}
    M -->|No| N[FAILED]
    M -->|Yes| O[Store Artifact]
    O --> P[COMPLETED]
```

### Key Methods in JavaCVRenderProvider

1. **`render(String jobId, String aiScript, String profile)`**
   - Main entry point
   - Parses AI script or timeline
   - Calls appropriate render method
   - Returns `RenderResult` with artifact metadata

2. **`renderFromTimeline()`**
   - Processes OTIO JSON timeline
   - Extracts clips, start times, duration
   - Handles subtitle tracks
   - Applies burn-in subtitles

3. **`transcodeVideo()`**
   - FFmpeg-based transcoding
   - Supports clipping by time range
   - Maintains audio/video sync

4. **`renderPlaceholderVideo()`**
   - Generates test video with frames
   - Used when no source material available

## Current Limitations

### ❌ Not Supported
- **Multi-track compositing**: Only first track processed
- **Complex transitions**: Basic fades only
- **Full subtitle burn-in**: Partial implementation (logs filter, but doesn't fully integrate)
- **GPU acceleration**: CPU-only processing
- **Remote workers**: All rendering in-process
- **OFX/GStreamer/MLT**: Planned but not implemented
- **H.265 encoding**: Not yet supported
- **HDR video**: Not yet supported

### ✅ Partially Supported
- **Subtitle burn-in**: Framework in place, requires full FFmpeg filtergraph integration
- **4K rendering**: Supported but may be slow on CPU

## Frontend Integration

### Export Panel → Backend API

**Request:**
```json
POST /api/v1/render/jobs/submit
{
  "tenantId": "tenant_123",
  "projectId": "proj_456",
  "prompt": "Create a 30 second video...",
  "profile": "default_1080p"
}
```

**Response:**
```json
{
  "jobId": "rj_789",
  "status": "QUEUED"
}
```

**Polling:**
```http
GET /api/v1/render/jobs/{jobId}
```

**Status Mapping:**
- `ExportPanel.Progress` → `RenderJobStatus`
- `ExportPanel.Error` → `RENDER_FAILED`, `AI_GENERATION_FAILED`

## Error Handling

### Error Codes
- `RENDER-500-001`: General render failure
- `RENDER-409-001`: Quota exceeded
- `RENDER-404-001`: Job not found

### Retry Logic
- Failed jobs can be retried via `/render/jobs/{jobId}/retry`
- State machine validates retry eligibility
- Quota re-checked on retry

### Exception Handling
- `IllegalArgumentException`: 400 Bad Request
- `IllegalStateException`: 409 Conflict
- `PlatformException`: Maps to configured error codes
- All exceptions logged with jobId context

## Artifact Generation

1. **Storage**: Local filesystem (`/tmp/platform/artifacts/{jobId}/`)
2. **Registration**: Catalog service tracks artifacts
3. **Metadata**: Format, resolution, duration stored
4. **Cleanup**: Manual cleanup required (no TTL yet)

## Testing

```bash
# Run render module tests
./gradlew :render-module:test

# Test specific provider
./gradlew :render-module:test --tests JavaCVRenderProviderTest
```

### Test Coverage
- Profile support verification
- Capability checks
- OTIO timeline parsing
- Empty timeline handling
- Error scenarios

## Configuration

```yaml
app:
  storage:
    local-root: /tmp/platform
  render:
    providers:
      javacv:
        enabled: true  # Currently always enabled
```

## Security Notes

- ✅ No `Runtime.exec()` or `ProcessBuilder` in business code
- ✅ JavaCV uses JNI bindings directly
- ✅ File path validation against storage root
- ✅ Temporary files cleaned up after rendering
- ✅ No shell command concatenation

## Performance Characteristics

- **CPU-only**: No GPU acceleration
- **Memory**: ~2-4GB per concurrent render
- **Speed**: Real-time for 1080p, slower for 4K
- **Concurrency**: Limited by CPU cores
- **Disk**: Temporary storage for input/output

## Next Steps (Planned)

1. Implement multi-track compositing
2. Add GPU worker support
3. Implement remote render workers
4. Add H.265 encoding support
5. Full subtitle burn-in integration
6. MLT provider for advanced editing
7. OFX provider for professional effects

---

*This document reflects the current implementation as of 2026-05-12. For future capabilities and roadmap, see [render-provider-extension-roadmap.md](render-provider-extension-roadmap.md).*