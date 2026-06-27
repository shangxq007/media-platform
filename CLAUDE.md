# CLAUDE.md — media-platform

## Project Identity

This repository is Media Capability Platform.

Product is the canonical communication object.
Timeline is the canonical editing model.
StorageRuntime owns physical materialization, checksum, and storage references.
ProductRuntime owns Product lifecycle, metadata, dependency, and query.
OpenCue is ExecutionEnvironment, not ExecutionBackend.
FFmpeg/libass is the baseline subtitle burn-in path.

## Frozen Boundaries

Do not modify:
- Platform Kernel
- Stable SPI
- Product model semantics
- Timeline model semantics
- ExecutionJob / ExecutionTask / ExecutionCommand semantics
- Execution lifecycle semantics
- StorageRuntime semantics
- ProductRuntime semantics
- ProducerRuntime semantics
- Flyway V1 baseline

Do not introduce:
- Artifact Runtime
- new graph runtime
- provider/backend/environment/storage provider exposure in public API

## Current Development Mode

Single-agent controlled development.

Use one task per branch/worktree.
Do not auto-merge.
Do not auto-push.
Do not deploy.
Do not use production secrets.
Do not modify .env files.

## Required Workflow

Before changing code:
1. Read AGENTS.md if present.
2. Read AGENT_TASK.md.
3. Inspect existing code before adding new abstractions.
4. Prefer existing services and conventions.

Before final response:
1. Run targeted tests.
2. Show exact commands and results.
3. Report unrelated pre-existing failures separately.
4. Summarize architecture compliance.

## Safety Rules

Never commit secrets.
Never weaken security checks.
Never disable tests to pass.
Never change Flyway V1 baseline.
Never expose signed URLs or local filesystem paths in public API responses.

## Current Backend Chain

Input RAW_MEDIA Product
→ StorageRuntime.materialize()
→ TimelineRevision / RenderJob
→ FFmpeg/libass baseline render
→ RenderOutputRegistrationService
→ ProductRuntime READY FINAL_RENDER Product
→ ProductDependency lineage
