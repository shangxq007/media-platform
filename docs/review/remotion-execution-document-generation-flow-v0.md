# Remotion Execution Document Generation Flow v0

## Overview

Wires RemotionInputProps generation into the ProviderExecutionDocument generation flow. Remotion remains document-generation-only — NOT executable.

## Flow

```text
ProviderExecutionDocumentDraft
  → ProviderExecutionDocumentGenerationService
    → (if REMOTION_INPUT_PROPS_DOCUMENT)
    → RemotionProviderExecutionDocumentGenerator
      → RemotionInputPropsGenerator.generate(timeline)
      → RemotionInputPropsValidator.validate(props)
      → RemotionInputPropsSerializer.serialize(props)
      → ProviderExecutionDocumentGenerationResult
    → (if other type)
    → SKIPPED_NON_REMOTION
```

## Domain Model

### ProviderExecutionDocumentGenerationResult

- `documentId` — deterministic SHA-256 from draftId + providerName + documentType + contentHash
- `draftId`, `providerName`, `documentType` — source draft info
- `generationStatus` — GENERATED, GENERATED_WITH_WARNINGS, REJECTED_UNSUPPORTED, REJECTED_INVALID, SKIPPED_NON_REMOTION, FAILED_CLOSED
- `generationReady` — always false in v0
- `validationPassed` — whether validator passed
- `validationIssues` — list of issues
- `serializedDocument` — deterministic JSON (null if not generated)
- `metadata` — safe metadata map

### ProviderExecutionDocumentGenerationStatus

- `GENERATED` — document generated successfully
- `GENERATED_WITH_WARNINGS` — generated with validation warnings
- `REJECTED_UNSUPPORTED` — draft type not supported
- `REJECTED_INVALID` — validation failed
- `SKIPPED_NON_REMOTION` — non-Remotion draft skipped
- `FAILED_CLOSED` — null or invalid input

### RemotionProviderExecutionDocumentGenerator

Narrow adapter:
1. Verifies draft type is REMOTION_INPUT_PROPS_DOCUMENT
2. Calls RemotionInputPropsGenerator
3. Calls RemotionInputPropsValidator
4. Calls RemotionInputPropsSerializer
5. Returns ProviderExecutionDocumentGenerationResult

### ProviderExecutionDocumentGenerationService

Generic service:
- Routes Remotion drafts to RemotionProviderExecutionDocumentGenerator
- Skips all other draft types (FFmpeg, MLT, GPAC, etc.)
- Returns list of results for batch processing

## Safety

- generationReady always false
- No Remotion execution
- No Node/npm/npx invocation
- No StorageRuntime mutation
- No ProductRuntime mutation
- No public API exposure
- Serialized JSON contains no local paths, storage internals, raw commands, or secrets

## Unsupported v0 Features

Same as P1R.0: multiple inputs, effects, transitions, 3D, arbitrary JS, user templates, npm packages, network fetch.

## Architecture Rules

- FFmpeg remains only executable provider
- Remotion remains POC/SPIKE — not production eligible
- RenderPlanPolicyGuard still rejects Remotion execution
- LocalExecutionPlanRunner still FFmpeg-only
- Public API unchanged

## Future Prerequisites

1. ✅ RemotionInputProps generation
2. ✅ Generation flow wiring
3. Remotion CLI availability check
4. Remotion render command construction
5. Policy guard allowlist for Remotion
6. Execution environment target
7. Production eligibility review
