# P4-TRANSITION-1: Cross-Dissolve Implementation

## Summary

Implemented cross-dissolve transition support for Golden Render Project using ffmpeg's `xfade` filter.

## Implementation

### Filter Graph

```
# Trim each clip to exact duration
[0:v]trim=start=0:end=5,setpts=PTS-STARTPTS[vt0];
[1:v]trim=start=0:end=5,setpts=PTS-STARTPTS[vt1];
[2:v]trim=start=0:end=5,setpts=PTS-STARTPTS[vt2];

# Chain xfade: vt0 xfade vt1 -> result xfade vt2
[vt0][vt1]xfade=transition=fade:duration=1.0:offset=4.0[vxf1];
[vxf1][vt2]xfade=transition=fade:duration=1.0:offset=8.0[vxf2];

# Rename for downstream processing
[vxf2]null[vconcat];
```

### Offset Calculation

For N clips with duration D and transition duration T:
- First xfade offset = D - T
- Second xfade offset = 2D - 2T
- Nth xfade offset = N×(D - T)

Total duration = N×D - (N-1)×T

Example: 3 clips × 5s with 1s transitions = 15 - 2 = 13s

### Key Implementation Details

1. **Trim + setpts**: Each input clip is trimmed to exact duration and timestamps are reset to 0 using `setpts=PTS-STARTPTS`. This is critical for xfade to work correctly.

2. **Chained xfade**: For multiple clips, xfade is chained: result of first xfade is fed as input to next xfade.

3. **Transition duration**: Passed via timeline JSON `metadata.transitionDuration` field.

4. **Fallback**: When transitionDuration=0, uses simple `concat` filter (no transition).

### Code Changes

- `FFmpegCommandFactory.buildMultiTrackCommand()`: Added `transitionDuration` parameter
- `FFmpegRenderProvider.render()`: Extract and pass transitionDuration
- `FFmpegRenderProvider.extractTransitionDuration()`: Parse from timeline JSON
- `GoldenRenderE2ETest`: Added `shouldRenderGoldenTimelineWithCrossDissolve` test

### Test Results

- GoldenRenderE2ETest: 7/7 passed ✅
- render-module full: all passed ✅
- readiness: PASS 63, FAIL 0, WARN 3 ✅

### Output

- `test-assets/golden-render-project-v1/outputs/final_1080p.mp4`
- Duration: 13s (3 clips × 5s - 2 transitions × 1s)
- Video: H.264 1920x1080 yuv420p
- Audio: AAC 48000Hz 2ch

## Classification

| Effect | Type | Status |
|--------|------|--------|
| fade_in | temporal | ✅ Implemented (P4-TEMPORAL-1) |
| fade_out | temporal | ✅ Implemented (P4-TEMPORAL-1) |
| cross_dissolve | transition | ✅ Implemented (P4-TRANSITION-1) |
| wipe | transition | ❌ Unsupported |
| slide | transition | ❌ Unsupported |
| zoom | transition | ❌ Unsupported |

## Next Steps

- P4-SPATIAL-1: Implement crop/transform runtime
- P4-OTIO-1: OTIO roundtrip validation
- P4-EXPORT-1: Metadata-only project export
