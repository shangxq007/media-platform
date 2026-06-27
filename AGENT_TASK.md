# Task: Backend R6.1 — TimelineRevision Render API Input Product Resolution

## Goal

Harden the TimelineRevision Render API so that rendering a TimelineRevision resolves Product-backed input media from timeline source assets, materializes those input Products through StorageRuntime, and renders with FFmpeg/libass using the materialized input path.

## Current R6 Gap

The R6 API can render a TimelineRevision, but inputProductIds are empty because input Products are not resolved from TimelineRevision sourceAssetIds.

## Target Chain

TimelineRevision
→ sourceAssetId
→ READY RAW_MEDIA Product via ownerAssetId / findByAsset
→ StorageRuntime.materialize()
→ FFmpeg/libass uses -i <materializedInputPath>
→ RenderOutputRegistrationService
→ READY FINAL_RENDER Product
→ ProductDependency lineage

## Hard Rules

- Do not modify Kernel/SPI/Product/Timeline/Execution semantics.
- Do not introduce Artifact Runtime.
- Do not introduce new graph runtime.
- Do not expose provider/backend/environment/storageProvider in public API.
- Do not allow API caller to choose FFmpeg/Remotion/OpenCue/provider/backend/environment/storage provider.
- Do not expose signed URLs.
- Do not expose local filesystem paths or materialized input paths.
- Do not use Remotion for baseline subtitle burn-in.
- Do not require OpenCue.
- Do not require MinIO/S3.
- Do not auto commit, push, merge, or deploy.

## R6.1 Specific Requirements

1. Add TimelineInputProductResolver as an internal @Service.
2. Resolve sourceAssetIds from TimelineRevision / TimelineRenderJobMapper.MappingResult to input Product IDs.
3. Use ProductRuntimeService.findByAsset(assetId).
4. Accept only READY + RAW_MEDIA + MEDIA_FILE Products.
5. Fail closed if no input Product is resolved.
6. Validate sourceAssetIds before Product lookup.
7. Reject blank IDs, path traversal, home paths, absolute paths, backslash paths, file/http/https/s3/gs URLs, and exact-match internal provider/backend/storage hints.
8. De-duplicate inputProductIds.
9. Materialize input Products through RenderInputMaterializationService.
10. FFmpeg command must use "-i <materializedInputPath>".
11. No testsrc fallback is allowed in the API render path.
12. testsrc is allowed only to generate tiny RAW_MEDIA test fixtures before Product registration.
13. R6.1 is single-primary-input only; multiple inputs/tracks remain future hardening.
14. Pass inputProductIds into RenderProductProvenance.
15. Let RenderOutputRegistrationService create ProductDependency edges through existing R5 behavior.
16. Response may include inputProductIds and inputDependencyCount.
17. inputDependencyCount should mean the de-duplicated resolved inputProductIds count passed to output registration.
18. Formal lineage must be verified through ProductRuntime/ProductDependency queries in tests.

## Tests Required

Add or update tests proving:

- TimelineRevision sourceAssetId resolves to READY RAW_MEDIA Product.
- missing Product mapping fails closed.
- non-READY Product fails closed.
- non-RAW_MEDIA Product fails closed.
- unsafe sourceAssetIds are rejected before Product lookup.
- repeated sourceAssetIds are de-duplicated.
- FFmpeg command uses "-i <materializedInputPath>".
- FFmpeg command does not contain "lavfi" or "testsrc" in the R6.1 render path.
- output Product metadata includes inputProductIds.
- formal ProductDependency edge exists: output DERIVED_FROM input.
- response includes inputProductIds and inputDependencyCount.
- response does not include signed URLs, local paths, materialized paths, provider/backend/environment/storageProvider.

## Suggested Files

Create:
- render-module/src/main/java/com/example/platform/render/app/timeline/TimelineInputProductResolver.java
- render-module/src/main/java/com/example/platform/render/app/timeline/TimelineInputProductResolverResult.java
- render-module/src/test/java/com/example/platform/render/app/timeline/TimelineInputProductResolverTest.java

Modify as needed:
- render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRevisionRenderService.java
- render-module/src/main/java/com/example/platform/render/api/dto/TimelineRevisionRenderResponse.java
- render-module/src/test/java/com/example/platform/render/app/timeline/TimelineRevisionRenderServiceTest.java
- docs/runbook-e2e-render-flow.md
- docs/review/timeline-git-product-readiness.md
- docs/review/timeline-workbench-api.md
- docs/review/product-runtime-foundation.md
- docs/review/storage-runtime-foundation.md

## Required Commands

Run targeted tests:

./gradlew :render-module:test --tests "com.example.platform.render.app.timeline.TimelineInputProductResolverTest"
./gradlew :render-module:test --tests "com.example.platform.render.app.timeline.TimelineRevisionRenderServiceTest"
./gradlew :render-module:test --tests "com.example.platform.render.app.output.RenderOutputRegistrationServiceTest"
./gradlew :storage-module:test

Run more tests if impacted.

## Final Report

Report:
- Summary
- API Contract
- Files Changed
- Tests Run
- Acceptance Checks
- Architecture Compliance
- Known Failures
- Recommended Commit Message

## Final Output Rule

Do not print the full final report body in chat.

Write the full report to:

/tmp/claude-final-report.md

At the end, print only:

1. Final report path: /tmp/claude-final-report.md
2. git status --short
3. latest commit hash or hashes
4. one short validation summary, max 10 lines

Do not print:
- full report body
- full test logs
- full git diff
- generated source files
- generated documentation content
- long stack traces unless the task failed and the stack trace is essential

If a command produces long output, redirect it to a temp log file and summarize the result.

Use files for long content, not chat output.
