# Export Validation

> Doc index: [docs/README.md](./README.md).

## Overview

Export validation ensures that a user's export request complies with their entitlements, quotas, and budget before a render job is created. The validation is performed by `EntitlementPolicyService.validateExport()` and exposed via the `POST /api/v1/render/export/validate` endpoint.

## Export Validation Flow

```
ExportValidationRequest
  -> EntitlementPolicyService.validateExport()
    1. Check tier preset allowed?
    2. Check tier format allowed?
    3. Check GPU allowed? (if gpu_ preset)
    4. Check 4K allowed? (if 4k/2160p preset)
    5. Estimate cost via CostEstimationPort
    6. Check budget via BudgetGuardPort
    7. Build upgrade options
  -> ExportValidationResult (or EntitlementDecision)
```

## Request Format

```json
{
  "preset": "pro_1080p",
  "outputFormat": "mp4",
  "estimatedDurationSeconds": 120
}
```

Fields:
- `preset`: The requested render preset (e.g., `default_1080p`, `gpu_h264`, `team_4k`)
- `outputFormat`: Target format (`mp4`, `webm`, `mov`, `dash`, `hls`, `cmaf`)
- `estimatedDurationSeconds`: Estimated render duration in seconds

## Response Format

```json
{
  "allowed": true,
  "decision": "ALLOWED",
  "reasonCode": "TIER",
  "userFriendlyMessage": "Export request validated successfully",
  "currentTier": "PRO",
  "matchedPolicies": ["tier:PRO"],
  "matchedGrantId": null,
  "matchedOverrideId": null,
  "matchedWorkspacePoolId": null,
  "quotaRemaining": 298,
  "recommendedAlternative": null,
  "upgradeOptions": [],
  "expiresAt": null,
  "requiresReview": false,
  "legacyValidation": {
    "allowed": true,
    "reasonCode": "ALLOWED",
    "tier": "PRO",
    "requestedPreset": "pro_1080p",
    "recommendedPreset": "pro_1080p",
    "providerCandidates": ["javacv"],
    "estimatedCost": 0.05,
    "currency": "USD",
    "budgetResult": { "allowed": true, "warning": false },
    "upgradeOptions": [],
    "userFriendlyMessage": "Export request validated successfully",
    "violations": [],
    "recommendations": []
  }
}
```

## Validation Checks

### 1. Preset Check

Against `ExportCapabilityPolicy.allowedPresets` for the user's tier:

| Tier | Allowed Presets |
|------|----------------|
| FREE | `client_720p_watermarked`, `free_720p_watermarked`, `default_720p`, `preview_720p`, `mobile_480p` |
| PRO | + `default_1080p`, `pro_1080p`, `social_1080p`, `social_720p`, `hq_1080p`, `h265`, `vp9` |
| TEAM | + `team_4k`, `ofx_1080p`, `ofx_720p`, `gpu_h264`, `gpu_h265` |
| ENTERPRISE | + `enterprise_4k_ofx`, `experimental_all_providers` |
| EXPERIMENTAL | Same as ENTERPRISE |

### 2. Format Check

Against `ExportCapabilityPolicy.allowedFormats`:

| Tier | Formats |
|------|---------|
| FREE | mp4, webm |
| PRO | mp4, webm, mov |
| TEAM+ | mp4, webm, mov, dash, hls, cmaf |

### 3. GPU Check

If the preset starts with `gpu_`, the `ProviderAccessPolicy.gpuAllowed()` must be true. Only TEAM, ENTERPRISE, and EXPERIMENTAL tiers allow GPU.

### 4. Resolution Check

If the preset contains `4k` or `2160p`, the `ExportCapabilityPolicy.maxResolutionHeight()` must be >= 2160. Only TEAM+ supports 4K.

### 5. Cost Estimation

If `CostEstimationPort` is available, the system estimates the cost:
- Selects provider based on preset and tier
- Estimates cost from duration, format, and GPU usage
- Returns estimated cost in the response

### 6. Budget Check

If `BudgetGuardPort` is available, the system checks the tenant's budget:
- If budget exceeded: adds `BUDGET_EXCEEDED` violation
- If approaching limit: adds warning recommendation

### 7. Client vs server render location

`POST /render/export/validate` accepts optional `effectKeys` and `timelineJson`. The response includes:

| Field | Description |
|-------|-------------|
| `recommendedRenderLocation` | `CLIENT` (browser WebCodecs/MediaRecorder) or `SERVER` (existing render job pipeline) |
| `clientExportSupported` | `true` when the timeline can be encoded locally |
| `clientExportUnsupportedReasons` | e.g. `EFFECT_REQUIRES_SERVER`, `DURATION_EXCEEDS_CLIENT_LIMIT`, `TIER_USES_SERVER_EXPORT` |

**FREE tier** defaults to `CLIENT` for `client_720p_watermarked` / `free_720p_watermarked` when:

- Feature flag `export.client.enabled` is on (tier policy)
- Duration ≤ 300 seconds
- No Natron/OFX/GPU effects in the timeline

**Browser upload flow** (optional artifact):

1. `POST /api/v1/render/client-exports` → `sessionId`, `uploadUrl`
2. Browser encodes via `ClientCompositor` (frontend)
3. `POST /api/v1/render/client-exports/{sessionId}/upload` (multipart) → `artifactId`, `downloadUrl`

See [frontend client export capabilities](../frontend/src/clientExport/clientExportCapabilities.ts) for browser support matrix.

## Recommended Presets

When the requested preset is not allowed, the system recommends an alternative:

| Tier | Recommended Preset |
|------|-------------------|
| FREE | `client_720p_watermarked` |
| PRO | `default_1080p` |
| TEAM | `default_1080p` |
| ENTERPRISE | `pro_1080p` |
| EXPERIMENTAL | `default_1080p` |

## Provider Candidates

The system selects provider candidates based on the preset:

| Preset Pattern | Provider |
|---------------|----------|
| `ofx_` | ofx |
| `gpu_` | remote-javacv |
| default | javacv |

## Upgrade Options

When validation fails, the response includes upgrade options:

| Violation | Upgrade Option |
|-----------|---------------|
| `PRESET_NOT_ALLOWED` | "Upgrade to PRO for 1080p and more presets" |
| `RESOLUTION_NOT_ALLOWED` | "Upgrade to TEAM for 4K and GPU rendering" |
| `GPU_NOT_ALLOWED` | "Upgrade to TEAM tier for GPU rendering access" |
| `FORMAT_NOT_ALLOWED` | "Upgrade to PRO for additional export formats" |
| `BUDGET_EXCEEDED` | "Reduce export quality or contact administrator to increase budget" |

## Frontend Integration

The frontend Export Panel calls `POST /api/v1/render/export/validate` before submitting a render job:

```typescript
const validateExport = async (preset: string, format: string, duration: number) => {
  const response = await fetch('/api/v1/render/export/validate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': currentTenant,
      'X-User-ID': currentUser,
    },
    body: JSON.stringify({
      preset,
      outputFormat: format,
      estimatedDurationSeconds: duration,
    }),
  });
  return response.json();
};

// Usage
const validation = await validateExport('team_4k', 'mp4', 300);
if (!validation.allowed) {
  // Show upgrade options from validation.upgradeOptions
  // Suggest recommendedAlternative preset
  showUpgradePrompt(validation.upgradeOptions, validation.recommendedAlternative);
} else {
  // Proceed with render job submission
  submitRenderJob(preset, format);
}
```

## Error Codes

| Code | Description |
|------|-------------|
| `EXPORT-403-001` | Preset not allowed for tier |
| `EXPORT-403-002` | Format not allowed for tier |
| `EXPORT-403-003` | GPU not allowed for tier |
| `EXPORT-403-004` | Resolution not allowed for tier |
| `EXPORT-403-005` | Budget exceeded |
| `EXPORT-422-001` | Invalid export request |
