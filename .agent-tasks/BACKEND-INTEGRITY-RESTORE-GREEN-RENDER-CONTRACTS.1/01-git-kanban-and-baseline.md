# Git, Kanban and Baseline

## Git State

```text
Branch: fix/pre-v5-readiness-recovery
HEAD: 7a05cdd
```

## Environment

```text
FFmpeg: 7.0.2-static (johnvansickle.com)
Path: ~/.local/bin/ffmpeg
SHA-256: e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99
libx264: YES
FFprobe: ~/.local/bin/ffprobe (7.0.2-static)
```

## Render Module Baseline

```text
Module: :render-module:test
Total tests: 2795
Passed: 2647
Failed: 39
Skipped: 63
```

## Failing Classes (14 total)

| # | Class | Failures |
|---|-------|-------:|
| 1 | RenderPipelineE2ECharacterizationTest | 11 |
| 2 | TimelineRevisionRenderServiceTest | 6 |
| 3 | TimelineRevisionRenderModeParityTest | 6 |
| 4 | RenderOrchestratorServiceCharacterizationTest | 4 |
| 5 | TimelineRevisionRenderExecutionModeTest | 3 |
| 6 | TimelineEffectApiProductizationTest | 1 |
| 7 | RenderJobRepositoryTest | 1 |
| 8 | TimelineSpecTest | 1 |
| 9 | FFmpegLibassBasicRenderPlannerTest | 1 |
| 10 | BasicTimelineEditingModelTest | 1 |
| 11 | CompileDomainBoundaryTest$RenderExecutionPlanContract | 1 |
| 12 | RenderJobStateMachineErrorModelTest$ValidTransitions | 1 |
| 13 | TimelineRevisionRealRenderSmokeTest | 1 |
| 14 | StorageRuntimeServiceBoundaryTest$ExistenceCheck | 1 |

## Architecture Guard

```text
32/32 passed
```
