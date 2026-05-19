# LiteFlow + Temporal Mixed Orchestration Architecture

> **Last updated**: 2026-01-11

## Overview

media-platform uses a hybrid orchestration approach:
- **Module-internal**: LiteFlow for intra-module business logic
- **Module-inter**: Temporal Workflow for cross-module task orchestration

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Timeline     │  │ Export       │  │ Effects      │              │
│  │ Editor       │  │ Panel        │  │ Panel        │              │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘              │
│         │                 │                                          │
│         └─────────────────┼──────────────────────────────────────────┤
│                           │ REST API                                 │
├───────────────────────────┼─────────────────────────────────────────┤
│  Backend                  │                                         │
│  ┌────────────────────────┴─────────────────────────────────────┐   │
│  │                    Temporal Workflow                          │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │   │
│  │  │ Render   │→│ Storage  │→│ Notify   │→│ Audit    │     │   │
│  │  │ Activity │  │ Activity │  │ Activity │  │ Activity │     │   │
│  │  └────┬─────┘  └──────────┘  └──────────┘  └──────────┘     │   │
│  │       │                                                       │   │
│  │  ┌────┴──────────────────────────────────────────────────┐    │   │
│  │  │              LiteFlow (Module Internal)                │    │   │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │    │   │
│  │  │  │ AI Script│→│ Render   │→│ Video    │→│ Artifact  │    │   │
│  │  │  │ Gen      │  │ Plan     │  │ Frame    │  │ Update    │    │   │
│  │  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │   │
│  │  └──────────────────────────────────────────────────────────┘   │
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

## Module-Internal Flow (LiteFlow)

Each module uses LiteFlow chains for internal business logic:

### Render Module Chain
```
aiScriptGen → renderPlanCalc → videoFrameGen → artifactUpdate
```

### With Subtitles
```
aiScriptGen → renderPlanCalc → subtitleBurnIn → videoFrameGen → artifactUpdate
```

### Policy-Governance Module Chain
```
policyCheck → featureFlagEval → routeDecision → executeAction
```

## Module-Inter Flow (Temporal)

Cross-module orchestration uses Temporal Workflows:

### Render Pipeline Workflow
```
1. User submits render job
2. RenderModule.renderJob() → generates video
3. StorageModule.storeArtifact() → stores output
4. NotificationModule.notifyCompletion() → notifies user
5. AuditModule.recordAudit() → records audit trail
```

### Workflow Features
- **Retry**: Configurable retry policies per activity
- **Timeout**: Per-activity timeout limits
- **Cancellation**: Signal-based cancellation
- **State Persistence**: Workflow state persisted to Temporal server
- **Human Review**: Failed steps can be marked for manual review

## Error Handling

All errors use the configurable error code system (Prompt 30):
- `RENDER-500-001`: Render execution failed
- `SUBTITLE-400-001`: Subtitle parsing failed
- `SUBTITLE-404-001`: Font not found
- `RENDER-503-001`: No render provider available

## Frontend Status Display

The Export Panel shows:
- Current workflow status (idle/running/completed/failed)
- Per-step status with progress bar
- Error codes with i18n messages
- Font information and fallback status
- Subtitle track information

## Configuration

### LiteFlow (render-pipeline.xml)
```xml
<flow>
    <chain name="render-pipeline">
        THEN(aiScriptGen, renderPlanCalc, videoFrameGen, artifactUpdate)
    </chain>
</flow>
```

### Temporal (application.yml)
```yaml
spring:
  temporal:
    namespace: media-pipeline
    workers:
      - task-queue: render-pipeline
        capacity: 10
```
