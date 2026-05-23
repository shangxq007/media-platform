# Browser client export — capability matrix

## Supported browsers (target)

| Browser | Preview (Program Monitor) | Export encode | Notes |
|---------|---------------------------|---------------|-------|
| Chrome 94+ | Yes | Yes (`MediaRecorder` + canvas) | Primary target |
| Edge 94+ | Yes | Yes | Chromium-based |
| Firefox 130+ | Yes | Yes (WebM) | VP9 WebM output |
| Safari 16.4+ | Partial | Partial | Stricter CORS; WebM only |

## Required APIs

- `HTMLCanvasElement` + 2D context
- `HTMLVideoElement` with CORS-enabled media URLs
- `MediaRecorder` **or** `VideoEncoder` (future hardening)

Detected via `detectClientExportCapabilities()` in the frontend.

## Unsupported cases (server fallback)

- Timeline effects: `video.natron_*`, OFX, GPU presets
- Duration &gt; 300 seconds on FREE tier
- PRO+ default exports (`TIER_USES_SERVER_EXPORT`) unless `client_720p_watermarked` is selected

## Feature flag

- `export.client.enabled` — enabled for FREE tier in `EntitlementPolicyService` seed flags
