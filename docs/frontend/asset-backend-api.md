# Asset Backend API — MVP

**Status:** Implemented
**Date:** 2026-06-12

---

## Endpoints

### List Project Assets
```
GET /api/v1/projects/{projectId}/assets
```
Returns all assets for the specified project, scoped to the current tenant.

**Response:** `200 OK`
```json
[
  {
    "id": "asset-abc123",
    "tenantId": "tenant-1",
    "projectId": "proj-1",
    "storageKey": "tenant/workspace/project/assets/asset-abc123/video.mp4",
    "mediaType": "VIDEO",
    "filename": "video.mp4",
    "sizeBytes": 1048576,
    "checksum": "sha256:...",
    "durationMs": 10000,
    "width": 1920,
    "height": 1080,
    "createdAt": "2026-06-12T10:00:00Z"
  }
]
```

### Get Asset by ID
```
GET /api/v1/projects/{projectId}/assets/{assetId}
```
Returns a single asset, scoped to tenant + project.

### Register Asset
```
POST /api/v1/projects/{projectId}/assets/register
```
Register a new asset reference (metadata only — no file upload).

**Request:**
```json
{
  "storageKey": "tenant/workspace/project/assets/asset-1/video.mp4",
  "mediaType": "VIDEO",
  "filename": "video.mp4",
  "sizeBytes": 1048576,
  "checksum": "sha256:abc123",
  "durationMs": 10000,
  "width": 1920,
  "height": 1080
}
```

**Response:** `201 Created`

### Get Preview URL
```
GET /api/v1/projects/{projectId}/assets/{assetId}/preview-url
```
Returns a preview URL for the asset.

**Response:**
```json
{
  "previewUrl": "/api/v1/projects/proj-1/assets/asset-1/raw"
}
```

### Delete Asset
```
DELETE /api/v1/projects/{projectId}/assets/{assetId}
```

## Security

- All endpoints require tenant context (JWT or API Key)
- Storage keys validated via `StorageKeyPolicy.assertValidPath()`
- Rejects: traversal, URL schemes, absolute paths, backslashes, null bytes
- Tenant + project scoping enforced on all queries

## Frontend Integration

**Status:** ✅ Integrated (2026-06-12)

The `AssetPicker` component now calls `GET /api/v1/projects/{projectId}/assets` via React Query (`useAssets` hook) to load real assets from the backend API. The `AssetPreview` component uses `GET /api/v1/projects/{projectId}/assets/{assetId}/preview-url` for media preview.

### Files

| File | Purpose |
|------|---------|
| `frontend/src/api/assets.ts` | Asset API client (list, get, preview-url, register) |
| `frontend/src/hooks/useAssets.ts` | React Query hooks + AssetSummary type |
| `frontend/src/components/assets/AssetPicker.tsx` | Asset browser with real API data |
| `frontend/src/components/assets/AssetPreview.tsx` | Media preview using preview-url API |

### Security Notes

- `storageKey` is NOT exposed to the frontend — only `filename` and `mediaType` are shown
- Preview URLs are fetched on demand via the API, not cached in local storage
- No signed URLs are logged or persisted
