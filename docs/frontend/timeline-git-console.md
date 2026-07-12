# Timeline Git Console

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** FRONTEND-TIMELINE-GIT-CONSOLE.0

---

## Route

`/dev/timeline-git`

---

## Features

| Feature | Status |
|---------|--------|
| Revision list | ✅ |
| Head revision marker | ✅ |
| Revision detail | ✅ |
| Snapshot JSON viewer | ✅ |
| Semantic diff | ✅ |
| Render selected revision | ✅ |
| Restore revision | ✅ |
| Restore confirmation | ✅ |
| Provenance display | ✅ |
| Merge experimental label | ✅ |

---

## API Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/timeline/revisions` | GET | List revisions |
| `/timeline/revisions/{id}` | GET | Get revision |
| `/timeline/revisions/{id}/snapshot` | GET | Get snapshot |
| `/timeline/revisions/compare` | GET | Semantic diff |
| `/timeline/revisions/{id}/render` | POST | Render revision |
| `/timeline/revisions/{id}/restore` | POST | Restore revision |

---

## Stack

- React 19
- TanStack Router
- TanStack Query
- Axios

---

## Status

- FRONTEND-TIMELINE-GIT-CONSOLE.0: COMPLETE
- Internal dev console: IMPLEMENTED
