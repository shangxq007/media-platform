# Render Job Dashboard

**Status:** Implemented
**Date:** 2026-06-12
**Route:** `/render-jobs`

---

## Purpose

Minimal render job dashboard for viewing job history, status, and artifacts. Uses TanStack Query for server state management. **This is NOT a full admin system** — it has no analytics, realtime updates, or complex filtering.

## Features

1. **Job List** — `GET /render/jobs` with status filter (ALL/QUEUED/PROCESSING/COMPLETED/FAILED/CANCELLED)
2. **Job Detail** — profile, project, snapshot, retry/cancel actions
3. **Artifact View** — video/image/audio preview with URI fallback
4. **Polling** — auto-refresh every 10s (jobs), 5s (selected job), stops on terminal status

## Backend APIs Used

| API | Endpoint | Purpose |
|-----|----------|---------|
| `GET /render/jobs` | List all jobs | Job list with tenant filtering |
| `GET /render/jobs/{jobId}` | Get job detail | Status, profile, project |
| `GET /render/jobs/{jobId}/artifacts` | Get artifacts | URI, format |
| `POST /render/jobs/{jobId}/retry` | Retry job | UI stub — backend must support |
| `POST /render/jobs/{jobId}/cancel` | Cancel job | UI stub — backend must support |

## Files

| File | Purpose |
|------|---------|
| `frontend/src/pages/RenderJobDashboard.tsx` | Main dashboard page |
| `frontend/src/components/render-jobs/JobList.tsx` | Job list with status filter |
| `frontend/src/components/render-jobs/JobDetail.tsx` | Job detail with retry/cancel |
| `frontend/src/components/render-jobs/ArtifactView.tsx` | Artifact preview |
| `frontend/src/api/render-jobs.ts` | API client + TanStack Query hooks |

## Security Notes

- No signed URL logging
- No artifact URL caching
- Open artifact links use `rel="noreferrer noopener"`
- No local persistence of job data

## Next Steps

- FRONTEND-EFFECT-PARAMETER-FORM
- FRONTEND-SUBTITLE-EDITOR
- FRONTEND-TIMELINE-CANVAS
