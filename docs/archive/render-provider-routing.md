# Render Provider Routing

> **Last updated**: 2026-05-11

## Routing Flow

```
ExportPanel → ExportPolicyService → RenderProviderRouter → RenderProvider
                  │                        │
                  ▼                        ▼
            ExportTier              RenderProviderRegistry
            ExportPreset            RenderProviderSelectionPolicy
            EffectKeys              RenderProviderFallbackPolicy
```

## Provider Selection

1. **Profile-based**: `ofx_*` profiles → OFXRenderProvider
2. **Effect-based**: Effects like blur/vignette → OFXRenderProvider
3. **Tier-based**: FREE → JavaCV only; PRO+ → OFX available
4. **Fallback**: If preferred provider unhealthy → any healthy provider

## Effect Key Mapping

| Effect Key | JavaCV | OFX | Category |
|------------|--------|-----|----------|
| `video.fade_in` | ✅ | ✅ | transition |
| `video.blur` | ✅ | ✅ | video |
| `video.vignette` | ❌ | ✅ | video |
| `video.chromatic` | ❌ | ✅ | video |
| `text.subtitle_burn_in` | ✅ | ✅ | text |
| `audio.volume` | ✅ | ✅ | audio |
| `video.overlay` | ❌ | ✅ | compositor |

## Quality Validation

After rendering, `RenderQualityCheckService` validates:
- Resolution matches preset
- Video codec matches preset
- Duration is reasonable (> 100ms)
- File size is non-trivial (> 100 bytes)

If validation fails → `RenderJob.status = QUALITY_CHECK_FAILED`
