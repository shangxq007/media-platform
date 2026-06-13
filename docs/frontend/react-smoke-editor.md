# React Smoke Editor

**Status:** Implemented (with Asset Picker + Preview)
**Date:** 2026-06-12
**Route:** `/smoke-editor`

---

## Purpose

Minimal React editor shell for full-stack smoke verification. Validates that the frontend can call backend APIs to complete the render flow. **This is NOT a full editor** — it has no timeline canvas, drag-drop, waveform, or advanced UI.

## Supported Flow

1. **Open** `/smoke-editor`
2. **Browse** demo assets in Asset Browser (left panel)
3. **Select** a video/image/audio asset — auto-fills asset URI
4. **Preview** the selected asset (video player, image, audio controls)
5. **Configure** clip start/end, optional subtitle text, optional effect
6. **Submit** render job
7. **Poll** job status automatically
8. **Preview** rendered artifacts (video/image/audio player, or URI fallback)

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
| `frontend/src/components/assets/AssetPicker.tsx` | Asset browser with demo assets |
| `frontend/src/components/assets/AssetPreview.tsx` | Video/image/audio preview |
| `frontend/src/components/artifacts/ArtifactPreview.tsx` | Rendered artifact preview |
| `frontend/src/api/smoke-editor.ts` | API client wrapper |

## Known Limitations

- **Demo assets only** — AssetPicker shows hardcoded demo assets. In production, a backend API for listing project assets is needed.
- **No real asset upload** — uses URI reference only
- **No timeline canvas** or visual editing
- **No effect parameter validation** beyond basic sliders
- **No auth integration** — uses dev token if available
- **Polling stops after 5 minutes**
- **No retry on poll failure**
- **No thumbnail generation** for artifacts

## Next Steps

- ✅ FRONTEND-RENDER-JOB-DASHBOARD — `/render-jobs` with job list, detail, artifacts
- FRONTEND-EFFECT-PARAMETER-FORM
- FRONTEND-SUBTITLE-EDITOR
