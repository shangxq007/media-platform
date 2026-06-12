# React Smoke Editor

**Status:** Implemented
**Date:** 2026-06-12
**Route:** `/smoke-editor`

---

## Purpose

Minimal React editor shell for full-stack smoke verification. Validates that the frontend can call backend APIs to complete the render flow. **This is NOT a full editor** — it has no timeline canvas, drag-drop, waveform, or advanced UI.

## Supported Flow

1. **Open** `/smoke-editor`
2. **Configure** timeline input (project ID, asset URI, clip start/end)
3. **Optional** add subtitle text
4. **Optional** add effect (blur or vignette)
5. **Submit** render job
6. **Poll** job status automatically
7. **View** artifact URI on completion

## Backend APIs Used

| API | Endpoint | Purpose |
|-----|----------|---------|
| `RenderAPI.saveTimelineSnapshot` | `POST /render/timeline-snapshots` | Create timeline snapshot |
| `RenderAPI.createJob` | `POST /render/jobs` | Submit render job |
| `RenderAPI.getJob` | `GET /render/jobs/{jobId}` | Poll job status |
| `RenderAPI.getArtifacts` | `GET /render/jobs/{jobId}/artifacts` | Get artifact output |

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | (empty, uses proxy) | API base URL |

## Files

| File | Purpose |
|------|---------|
| `frontend/src/pages/SmokeEditorPage.tsx` | Main page with state management |
| `frontend/src/components/smoke-editor/SmokeEditorForm.tsx` | Input form |
| `frontend/src/components/smoke-editor/RenderJobStatusPanel.tsx` | Job status display |
| `frontend/src/components/smoke-editor/ArtifactPanel.tsx` | Artifact output display |
| `frontend/src/api/smoke-editor.ts` | API client wrapper |

## Known Limitations

- No timeline canvas or visual editing
- No real asset upload — uses URI reference
- No effect parameter validation UI beyond basic sliders
- No auth integration (uses dev token if available)
- Polling stops after 5 minutes
- No retry on poll failure

## Next Steps

- FRONTEND-ASSET-PICKER-AND-PREVIEW
- FRONTEND-SUBTITLE-EDITOR
- FRONTEND-EFFECT-PARAMETER-UI
