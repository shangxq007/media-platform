# Editor: Export Flow Guide

> **Purpose:** Document the export panel, preset selection, render job submission, artifact handling, and error handling.  
> **Component:** `ExportPanel.vue`, `RenderJobStatus.vue`, `ArtifactResult.vue`, `ArtifactPreviewModal.vue`  
> **Last Updated:** 2026-05-16 (Prompt 62)

---

## Export Panel Overview

The **Export Panel** (`ExportPanel.vue`) is the right-side panel tab that provides the complete video export workflow. It integrates with the entitlement system, render pipeline, and artifact catalog.

### Layout

```
┌──────────────────────────────────────┐
│  Export                    [FREE]    │
├──────────────────────────────────────┤
│  Timeline Summary                    │
│  Duration: 30.0s  Tracks: 2          │
│  Clips: 3         Subtitles: 1       │
│  Effects: 1                           │
├──────────────────────────────────────┤
│  Budget                    [OK]       │
│  ████████████░░░░░░░░  $0.50/$10.00  │
├──────────────────────────────────────┤
│  Render Worker                       │
│  [Local] [Remote]                    │
│  ● Local Worker        auto          │
├──────────────────────────────────────┤
│  Preset                              │
│  [Free 720p (Watermarked)    ▼]     │
├──────────────────────────────────────┤
│  Estimated Cost                      │
│  $0.0100 USD                         │
│  Provider: stub                      │
├──────────────────────────────────────┤
│  Format    [MP4 (H.264)       ▼]    │
│  Frame Rate [30 fps            ▼]    │
│  Encoder   [H.264              ▼]    │
├──────────────────────────────────────┤
│  ┌──────────────────────────────────┐│
│  │        Export Video              ││
│  └──────────────────────────────────┘│
└──────────────────────────────────────┘
```

---

## Export Panel Usage

### Step 1: Timeline Summary

The timeline summary automatically reflects the current state of the timeline:
- **Duration** — Total timeline duration in seconds
- **Tracks** — Number of tracks with clips
- **Clips** — Total number of clips with effects
- **Subtitles** — Number of subtitle tracks
- **Effects** — Total effect count across all clips

### Step 2: Budget Status

The budget status bar shows:
- **Progress bar** — Visual representation of budget consumption
- **Spend / Limit** — Current spending vs budget limit
- **Status indicator** — OK (green), Warning (yellow), or Exceeded (red)
- **Warning message** — Displayed when approaching or exceeding budget

When budget is exceeded, the export button shows "Budget Exceeded" and is disabled.

### Step 3: Worker Selection

Users can choose between two worker types:

| Worker | Availability | Description |
|--------|-------------|-------------|
| Local | All tiers | Runs on the local machine using the configured provider |
| Remote | TEAM+ only | Distributed render workers for parallel processing |

Remote workers display:
- Worker ID
- Status (IDLE, BUSY, OFFLINE, ERROR)
- Active jobs / Max concurrent jobs

### Step 4: Preset Selection

Presets define the output quality and format configuration:

| Tier | Available Presets |
|------|-------------------|
| FREE | `free_720p_watermarked`, `preview_720p`, `mobile_480p` |
| PRO | + `default_720p`, `default_1080p`, `pro_1080p`, `social_1080p`, `social_720p` |
| TEAM | + `team_4k`, `gpu_h264`, `gpu_h265`, `hq_1080p`, `h265`, `vp9` |
| ENTERPRISE | + All presets |
| EXPERIMENTAL | + All presets including experimental |

Each preset specifies:
- **Resolution** — e.g., `1280x720`, `1920x1080`, `3840x2160`
- **Watermark** — Whether a watermark is applied
- **Codec** — Derived from preset name (h264, h265, vp9)

### Step 5: Format, Frame Rate, Encoder

| Setting | Options |
|---------|---------|
| Format | MP4 (H.264), WebM (VP9), MOV |
| Frame Rate | 24 fps, 30 fps, 60 fps |
| Encoder | H.264, VP9, AAC |

### Step 6: Subtitle Mode

When subtitle tracks exist, users can choose:

| Mode | Description |
|------|-------------|
| No Subtitles | Subtitles not included in export |
| Burn-in (Hardcoded) | Subtitles rendered into the video frames |
| External Subtitle File | Separate subtitle file alongside video |
| Multi-language Package | Multiple subtitle tracks (only if 2+ tracks exist) |

### Step 7: Submit Export

Clicking "Export Video" triggers:
1. Export validation via `EntitlementAPI.validateExport()`
2. If blocked, displays error message and recommended preset
3. If allowed, submits render job via `RenderAPI.createJob()`
4. Starts polling for job status

---

## Render Job Submission and Status Tracking

### Submission Flow

```
User clicks "Export Video"
        │
        ▼
  validateCurrentExport()
        │
   ┌────┴────┐
   │         │
Allowed   Blocked
   │         │
   ▼         ▼
Create    Show error
RenderJob  + recommended
   │         preset
   ▼
Start polling
(3s interval)
```

### Render Job States

| State | Display | Actions Available |
|-------|---------|-------------------|
| `creating` | Initial state | — |
| `queued` | "In queue" badge | Cancel |
| `running` | Progress bar with percentage | Cancel |
| `completed` | Artifact result displayed | Preview, Download |
| `failed` | Error message + error code | Retry, Copy diagnostic |
| `cancelled` | "Cancelled" badge | — |

### RenderJobStatus Component

The `RenderJobStatus.vue` component displays:

- **Job ID** — The unique render job identifier
- **Status badge** — Color-coded by state (queued=blue, running=yellow, completed=green, failed=red, cancelled=gray)
- **Progress bar** — Visible when running or queued, shows percentage
- **Error info** — Error code and message when failed
- **Actions** — Retry (failed only), Cancel (running/queued), Copy diagnostic info

### Polling Mechanism

The export panel polls for job status every 3 seconds:

```
QUEUED → RENDERING → COMPLETED
                   → FAILED
```

On completion:
1. Polling stops
2. `completedArtifact` is created from the job data
3. `ArtifactResult` component renders instead of `RenderJobStatus`

---

## Artifact Preview and Download

### ArtifactResult Component

When a render job completes, `ArtifactResult.vue` displays:

| Field | Description |
|-------|-------------|
| Name | Export name derived from project + job ID |
| Format | Output format badge (MP4, WebM, etc.) |
| File size | Formatted as B/KB/MB |
| Duration | Formatted as mm:ss |
| Resolution | Width × Height (if available) | 
| Provider | Render provider used |
| Status | "Completed" badge |

### Actions

| Action | Button | Behavior |
|--------|--------|----------|
| Preview | "Preview" | Opens `ArtifactPreviewModal` |
| Download | "Download" | Opens output URL in new tab |
| Open in Catalog | "Open in Catalog" | Navigates to catalog (if `catalogId` present) |
| View Logs | "Render Logs" | Opens render logs URL (if present) |

### ArtifactPreviewModal

The `ArtifactPreviewModal.vue` provides in-browser preview:

| Format | Preview Type |
|--------|-------------|
| MP4, MOV, WebM | `<video>` element with controls |
| MP3, WAV, AAC, OGG | `<audio>` element with controls |
| PNG, JPG, GIF, WebP | `<img>` element |
| Other | Fallback message: "Preview not available for {FORMAT} format" |

The modal includes:
- Close button (top-right, aria-label="Close preview")
- Media element with `controls` attribute
- Footer with format and resolution info
- "Close" button in footer

---

## Error Handling

### Validation Errors

When export validation fails, the panel displays:

```
⚠ Export Blocked
{userFriendlyMessage}
• Violation 1
• Violation 2
Use recommended preset: {recommendedPreset}
[Upgrade Required card]
```

### Render Job Errors

When a render job fails:

```
Render Failed
Error Code: RENDER-500-001
Something went wrong
[Retry] [Copy diagnostic info]
```

### Error Codes

| Code | Meaning | Resolution |
|------|---------|------------|
| `RENDER-400-001` | Invalid render job request | Check format/resolution settings |
| `RENDER-404-001` | Render job not found | Refresh and retry |
| `RENDER-422-001` | Render quality check failed | Adjust quality settings |
| `RENDER-500-001` | Render execution failed | Retry or contact support |
| `RENDER-503-001` | No render provider available | Check worker status |
| `COMMON-404-001` | Artifact not found | Refresh artifact list |

### Diagnostic Info

For failed jobs, users can:
1. Click "Copy diagnostic info" to copy job details to clipboard
2. The diagnostic includes: Job ID, Status, Timestamp
3. Use the "Retry" button to resubmit the job

---

## Cost Estimation and Quota

### Cost Display

When a preset is selected, the panel shows:
- **Estimated cost** — Calculated from `validationResult.estimatedCost`
- **Currency** — e.g., USD
- **Provider** — Selected render provider
- **Quota remaining** — From `budgetStatus.remainingBudget`

### Unavailable Presets

Presets not available in the current tier are shown in a separate section:

```
Unavailable Presets (FREE)
GPU H.264 (NVENC)     GPU rendering requires TEAM tier or above
Team 4K               4K export requires TEAM tier or above
```

---

## Export Validation Blocking Conditions

The export button is disabled when:
1. No project is loaded (`!projectStore.hasProject`)
2. No tracks have clips (`!timelineStore.state.tracks.some(t => t.clips.length > 0)`)
3. Currently submitting (`submitting.value === true`)
4. Budget exceeded (`hasBudgetExceeded.value === true`)
5. Validation returned `allowed: false`

---

## Component Reference

| Component | File | Purpose |
|-----------|------|---------|
| `ExportPanel` | `components/export/ExportPanel.vue` | Main export workflow panel |
| `RenderJobStatus` | `components/export/RenderJobStatus.vue` | Job status display with actions |
| `ArtifactResult` | `components/export/ArtifactResult.vue` | Completed artifact display |
| `ArtifactPreviewModal` | `components/export/ArtifactPreviewModal.vue` | Media preview modal |
| `useRenderJob` | `composables/useRenderJob.ts` | Render job state management |
| `useArtifact` | `composables/useArtifact.ts` | Artifact state management |
| `useExportValidation` | `composables/useExportValidation.ts` | Export validation logic |
