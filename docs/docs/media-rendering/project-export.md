# Project Export v1

## Overview

Project Export v1 defines a standardized export package format for media platform projects. It enables users to backup, share, and archive their projects, and provides the foundation for future import capabilities.

## Export Scenarios

| Scenario | Description | Export Mode |
|----------|-------------|-------------|
| **Account Deletion** | User downloads project before account deletion | `metadata_only` or `linked_assets` |
| **Project Backup** | User saves a copy of their project | `metadata_only` |
| **Share / Collaborate** | User exports for another user to import | `linked_assets` |
| **Support Reproduction** | Support team exports to reproduce rendering issues | `render_reproduction` |
| **Golden Test Archive** | Golden Render Project archival for CI regression | `bundled_assets` |

## Export Modes

| Mode | Media Files | Signed URLs | Use Case |
|------|-------------|-------------|----------|
| `metadata_only` | ❌ | ❌ | Backup, share template, support |
| `linked_assets` | ❌ | ✅ (short-lived) | Share with existing storage access |
| `bundled_assets` | ✅ | ❌ | Full archive, Golden Test, deletion |
| `render_reproduction` | ✅ (outputs only) | ❌ | Support reproduction |

### Mode Details

#### metadata_only
- Contains only JSON manifests and text files
- No media files included
- No signed URLs generated
- Smallest package size (~KB)
- Import requires re-binding assets

#### linked_assets
- References existing storage objects
- Generates short-lived signed URLs (TTL configurable, default 24h)
- Signed URLs are revocable
- Requires storage access on import side
- Medium package size (~KB + URL list)

#### bundled_assets
- Includes all media files in the package
- No signed URLs needed
- Largest package size (~MB-GB)
- Self-contained, no external dependencies
- Suitable for long-term archival

#### render_reproduction
- Includes only rendered outputs + metadata
- No source assets
- Used by support team to reproduce rendering issues
- Smallest useful package for debugging

## Directory Structure

```
project-export-v1/
  manifest.json                          # Export package metadata
  project.json                           # Project metadata
  assets.json                            # Asset manifest
  timeline/
    timeline.json                        # Internal timeline
    timeline.otio                        # OTIO timeline
    revisions.json                       # Timeline revisions
  render/
    render-plan.json                     # Render plan
    spatial-plan.json                    # Spatial plan
    export-profiles.json                 # Output profiles
  subtitles/
    subtitles.srt                        # SRT subtitles
    subtitles.vtt                        # WebVTT subtitles
  effects/
    effect-taxonomy.json                 # Effect taxonomy version
    applied-effects.json                 # Applied effects log
  ai/
    edit-history.redacted.json           # AI edit history (redacted)
    variants.json                        # AI-generated variants
  audit/
    audit-summary.json                   # Audit summary
  outputs/
    outputs-manifest.json                # Render output manifest
  checksums/
    sha256sums.txt                       # File checksums
  README.md                              # Human-readable summary
```

## Schema Definitions

### manifest.json

```jsonc
{
  "$schema": "project-export-v1",
  "schemaVersion": "project-export-v1",
  "exportId": "export_<uuid>",
  "exportMode": "metadata_only",
  "exportedAt": "2026-06-02T23:44:00Z",
  "exportedBy": "user:<userId>",
  "project": {
    "projectId": "golden-render-project-v1",
    "name": "Golden Render Project v1",
    "description": "Standard acceptance project",
    "createdAt": "2026-05-29T00:00:00Z",
    "updatedAt": "2026-06-02T23:44:00Z",
    "durationMs": 25000,
    "frameRate": 30,
    "resolution": { "width": 1920, "height": 1080 },
    "status": "completed"
  },
  "compatibility": {
    "minPlatformVersion": "1.0.0",
    "effectTaxonomyVersion": "v1",
    "spatialPlanVersion": "v1",
    "otioSchema": "Timeline.1"
  },
  "security": {
    "containsSignedUrls": false,
    "containsMedia": false,
    "containsSecrets": false,
    "containsCredentials": false,
    "promptRedacted": true,
    "historyRedacted": true,
    "storageRefsRedacted": true
  },
  "assets": {
    "mode": "metadata_only",
    "count": 17,
    "totalSizeBytes": 0,
    "signedUrlTtlSeconds": null
  },
  "outputs": {
    "count": 3,
    "formats": ["mp4"]
  },
  "checksums": {
    "algorithm": "sha256",
    "file": "checksums/sha256sums.txt"
  }
}
```

### assets.json

```jsonc
{
  "schemaVersion": "project-export-v1",
  "exportMode": "metadata_only",
  "assets": [
    {
      "assetId": "color_bars_1080p",
      "filename": "color_bars_1080p.mp4",
      "type": "video",
      "mimeType": "video/mp4",
      "sizeBytes": 524288,
      "sha256": "abc123...",
      "duration": 10.0,
      "width": 1920,
      "height": 1080,
      "frameRate": 30,
      "codec": "h264",
      "storageRef": null,  // Redacted in metadata_only mode
      "downloadUrl": null   // Not included in metadata_only mode
    },
    {
      "assetId": "logo_transparent",
      "filename": "logo_transparent.png",
      "type": "image",
      "mimeType": "image/png",
      "sizeBytes": 131072,
      "sha256": "def456...",
      "width": 512,
      "height": 512,
      "storageRef": null,
      "downloadUrl": null
    }
  ],
  "signedUrls": null,  // Only present in linked_assets mode
  "signedUrlPolicy": {
    "ttlSeconds": 86400,
    "revocable": true,
    "auditRecord": true
  }
}
```

### signed URL Policy (Future: P4-EXPORT-2)

```jsonc
{
  "signedUrls": [
    {
      "assetId": "color_bars_1080p",
      "url": "https://storage.example.com/...?token=...",
      "expiresAt": "2026-06-03T23:44:00Z",
      "ttlSeconds": 86400,
      "revocable": true
    }
  ],
  "policy": {
    "maxTtlSeconds": 604800,
    "defaultTtlSeconds": 86400,
    "revocable": true,
    "auditRecord": true,
    "noPermanentUrls": true
  }
}
```

## Security & Privacy Rules

### Always Excluded
- Provider credentials (API keys, tokens)
- Storage backend secrets
- Internal service URLs
- Database connection strings
- Encryption keys

### Redacted by Default
- AI prompts and edit history (`promptRedacted: true`)
- User PII in audit logs
- Storage object references (`storageRefsRedacted: true`)
- IP addresses in access logs

### Conditionally Included
- Signed URLs: only in `linked_assets` mode, short-lived, revocable
- Media files: only in `bundled_assets` or `render_reproduction` mode
- Full audit log: only in `render_reproduction` mode for support

### Cross-Tenant Sharing
- Asset IDs are source-project references only
- Import must re-bind assets to target storage
- Original storage object permissions are NOT transferred
- Signed URLs (if present) are generated for the importing user

## Account Deletion Export Flow

```
1. User requests account deletion
2. System generates project export package (metadata_only or bundled_assets)
3. User downloads package
4. System confirms deletion with user
5. Account and projects are deleted
6. Signed URLs are revoked
7. Package download link expires
```

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│ User Request │────▶│ Generate     │────▶│ User        │
│ Deletion     │     │ Export       │     │ Downloads   │
└─────────────┘     └──────────────┘     └─────────────┘
                           │
                    ┌──────▼──────┐
                    │ Confirm     │
                    │ Deletion    │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Revoke URLs │
                    │ Delete Data │
                    └─────────────┘
```

## Import Preview Report (Future: P4-EXPORT-3)

When importing an export package, the system generates a preview report:

```jsonc
{
  "importPreview": {
    "compatible": true,
    "schemaVersionMatch": true,
    "warnings": [
      {
        "code": "MISSING_ASSET",
        "severity": "warning",
        "message": "Asset 'logo_transparent' not found in target storage",
        "assetId": "logo_transparent"
      },
      {
        "code": "UNSUPPORTED_EFFECT",
        "severity": "info",
        "message": "Effect 'video.particle_overlay' requires plugin X",
        "effectKey": "video.particle_overlay"
      }
    ],
    "assetMapping": [
      {
        "sourceAssetId": "color_bars_1080p",
        "targetAssetId": null,
        "status": "needs_upload",
        "sizeBytes": 524288
      }
    ],
    "estimatedImportSize": 524288,
    "missingAssetCount": 1,
    "unsupportedEffectCount": 1
  }
}
```

## Golden Render Project as Export Example

The Golden Render Project v1 can be wrapped in a project-export-v1 package:

```
golden-render-project-v1-export/
  manifest.json                    # exportMode: "bundled_assets"
  project.json                     # Golden Render Project metadata
  assets.json                      # 17 assets (video, image, audio, subtitle)
  timeline/
    timeline.json                  # 5 clips, 4 transitions
    timeline.otio                  # OTIO representation
    revisions.json                 # Revision history
  render/
    render-plan.json               # Operations: fade, crop, overlay, subtitle
    spatial-plan.json              # Crop regions, placements
    export-profiles.json           # preview_480p, final_1080p, debug_overlay
  subtitles/
    subtitles_zh.srt
    subtitles_en.srt
    subtitles_webvtt.vtt
  effects/
    effect-taxonomy.json           # v1 taxonomy
    applied-effects.json           # Applied: fade_in, cross_dissolve, crop, overlay
  ai/
    edit-history.redacted.json     # Empty for Golden Project
    variants.json                  # Empty
  audit/
    audit-summary.json             # Creation date, export date, version
  outputs/
    outputs-manifest.json          # final_1080p.mp4, crop_validation_1080p.mp4
  checksums/
    sha256sums.txt
  README.md                        # Human-readable project summary
  assets/                          # Only in bundled_assets mode
    video/
      color_bars_1080p.mp4
      grid_motion_1080p.mp4
      ...
    image/
      logo_transparent.png
      ...
    audio/
      music_bgm.wav
      ...
    subtitle/
      subtitles_zh.srt
      ...
```

## Validation Rules

### Schema Validation
- `manifest.json` must validate against project-export-v1 schema
- All required fields must be present
- `exportMode` must be one of the defined modes
- `schemaVersion` must match expected version

### Asset Validation
- Asset IDs must be unique
- SHA256 checksums must match file contents
- Media files must have valid metadata (duration, resolution)
- Subtitle files must be valid SRT/WebVTT

### Security Validation
- No secrets or credentials in any file
- Signed URLs must have expiration timestamps
- Storage refs must be redacted in metadata_only mode
- AI prompts must be redacted

## File Format Specifications

| File | Format | Required |
|------|--------|----------|
| manifest.json | JSON | ✅ |
| project.json | JSON | ✅ |
| assets.json | JSON | ✅ |
| timeline/*.json | JSON | ✅ |
| timeline/*.otio | JSON (OTIO) | ✅ |
| render/*.json | JSON | ✅ |
| subtitles/*.srt | SRT | ⚠️ If subtitles exist |
| subtitles/*.vtt | WebVTT | ⚠️ If subtitles exist |
| effects/*.json | JSON | ✅ |
| ai/*.json | JSON | ⚠️ If AI features used |
| audit/*.json | JSON | ✅ |
| outputs/*.json | JSON | ✅ |
| checksums/*.txt | Text | ✅ |
| README.md | Markdown | ✅ |

## Implementation History

### P4-EXPORT-1: Metadata-only API
- Added `ProjectExportController` at `POST /api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports`
- Added `ProjectExportService` for metadata-only export generation
- Added DTOs for full export response hierarchy
- **Permission**: reuses existing `assertTenantAccess(tenantId)` pattern
- **Security**: no signed URLs, no media files, storage refs null, prompts redacted
- **Audit**: records `PROJECT_EXPORT` via `AuditPort.record()`
- Tests: 11 tests covering all scenarios

### P4-EXPORT-2: Linked Assets with Signed URLs
- **API**: `POST .../exports` with `{ "mode": "linked_assets", "signedUrlTtlSeconds": 3600 }`
- **TTL policy**: default 3600s, max 86400s (24h), >86400 returns 400
- **Signed URL port**: `ProjectAssetExportPort` interface in `shared-kernel`
- **Adapter**: `ArtifactCatalogProjectAssetExportAdapter` in `identity-access-module`
  - Queries `ArtifactCatalogService.listArtifactsByProject(projectId)` for real assets
  - Calls `AssetDownloadUrlPort.generateSignedUrl()` for each asset
  - Filters out tombstoned/purged artifacts
- **Storage implementation**: `S3AssetDownloadUrlPort` in `storage-module`, wraps `BlobStorage.presignStorageUri()`
- **Response**: `security.containsSignedUrls=true`, `assets.mode=linked_assets`, per-asset `downloadUrl`
- **Security**: `storageRef` remains null, no internal paths/keys, signed URLs have finite TTL, URLs not in audit payload
- **Audit**: payload includes exportId, mode, tenantId, projectId, assetCount, ttlSeconds (no signed URLs)
- **Error strategy**: fail closed — if any asset cannot generate signed URL, entire export fails
- **No signing port**: returns 501 with clear error message
- **Tests**: 13+ tests covering linked_assets mode, TTL validation, default TTL, audit without URLs, fail-closed, wrong tenant rejection, adapter behavior, no-port behavior

### P4-EXPORT-3a: Project Import Preview
- **API**: `POST /api/v1/identity/tenants/{tenantId}/project-imports/preview`
- **Request**: `{ "exportPackage": { ...project-export-v1 structure... } }`
- **Response**: compatibility analysis with asset summary, effect summary, warnings, errors
- **Schema validation**: rejects unsupported schema versions with 400
- **Asset analysis**:
  - `metadata_only`: assets default to `needsUpload`
  - `linked_assets`: if `downloadUrl` present and not expired → `available`
  - Expired signed URLs generate warning
- **Effect compatibility**: checks effect keys against known taxonomy v1
  - Unknown effect keys generate `UNSUPPORTED_EFFECT` warning
- **Spatial validation**: validates normalized_ppm coordinates are integers in 0..1,000,000 range
- **Security**: no signed URL download, no URL in audit payload, no project creation, no DB writes
- **Audit**: records `PROJECT_IMPORT_PREVIEW` with exportId, mode, assetCount, ttlSeconds (no URLs)
- **Tests**: 8+ tests covering schema validation, asset analysis, effect compatibility, spatial validation, security

## Future Work

### P4-EXPORT-3b: Full Project Import
- Create project from export package
- Asset re-binding and upload
- Schema version migration
- Conflict resolution

### P4-EXPORT-4: Zip Packaging
- Compress export package
- Progress tracking for large projects
- Resume support

## References

- `docs/media-rendering/effect-taxonomy.md` — Effect taxonomy v1
- `docs/media-rendering/spatial-coordinate-system.md` — Spatial coordinate system v1
- `docs/media-rendering/golden-render-project.md` — Golden Render Project v1
- `test-assets/golden-render-project-v1/` — Example export source
