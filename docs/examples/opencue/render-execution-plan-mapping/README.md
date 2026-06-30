# RenderExecutionPlan-to-CJSL Mapping Examples

## Purpose

This directory contains design examples for mapping platform `RenderExecutionPlan` concepts to OpenCue CJSL job/layer/frame structures.

## Files

| File | Purpose |
|------|---------|
| `render-execution-plan-example.json` | Hypothetical RenderExecutionPlan input |
| `opencue-job-spec-draft-shared-path.json` | OpenCueJobSpecDraft for shared-path mode |
| `opencue-job-spec-draft-object-storage.json` | OpenCueJobSpecDraft for object-storage mode |
| `generated-cjsl-shared-path-example.xml` | Generated CJSL for shared-path execution |
| `generated-cjsl-object-storage-worker-materialization-example.xml` | Generated CJSL for object-storage mode |
| `dependency-stage-splitting-example.md` | How to handle dependencies without CJSL depends |
| `storage-strategy-comparison.md` | Shared path vs object storage comparison |
| `failure-status-mapping-example.md` | Failure visibility and status mapping |

## Architecture Boundary

These are **design examples only**. They do not implement:
- Production OpenCue adapter
- Live Cuebot submission
- StorageRuntime integration
- ProductRuntime integration
- Public API endpoints

## Usage

These examples inform the future production adapter design. They are not runtime code.
