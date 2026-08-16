# TIMELINE_INGRESS_AUTHORITY_CONTRACT_V1 (GCR-1 Correction V2)

Legal base: fa58312c53bb006a5fd519d2479cf3b087d4c604 (tree 61beb188c4c0b802b32637db20c824bba9c2d066)

## Authority owners (frozen)

| Concern | Sole owner |
|---|---|
| Canonical internal-1.0 validation (accept/reject) | timeline-module `InternalTimelineValidationService` (canonical gate: InternalTimelineCandidateAdapter + TimelineCanonicalValidator + TimelineCanonicalNormalizer) |
| Canonical internal-1.0 import/conversion (external → canonical) | timeline-module `TimelineImportService` (typed TimelineImportRequest in) |
| Canonical internal-1.0 construction/write | timeline-module `TimelineImportService` (sole canonical JSON constructor; deepCanonicalize + write) |
| Canonical serialization | timeline-module `InternalTimelineJson` (unchanged) |
| Canonical semantic diff / merge / revision / patch | timeline-module (unchanged, GCR-1 V1 accepted) |

## Allowed boundary adapters (outside Timeline, zero canonical authority)

- render `TimelineSpecImportAdapter`: TimelineSpec + TimelineExtensions → TimelineImportRequest
  (mechanical field mapping only; all canonical mapping/semantic construction stays in TimelineImportService)
- render `RenderTimelinePayloadCodec`: render-side implementation of Timeline-owned TimelinePayloadCodec port
  (delegates canonical construction to TimelineImportService via TimelineConversionService)
- render `TimelineConversionService`: interchange parsing + delegation coordinator
  (resolves editor/OTIO/legacy → TimelineSpec, maps via adapter, delegates construction to Timeline-owned service)

## Render downstream responsibilities (keep, non-authoritative)

- InternalTimelineAdapter: canonical → TimelineSpec projection (render planning)
- InternalTimelineToEditorConverter: canonical → editor v2 projection
- BaseJobTimelineLoader: read-only canonical load for render jobs
- TimelineSpecResolver: interchange resolution (internal→spec projection, legacy→spec parse)
- TimelineScriptParser: external/interchange parser (TimelineSpec JSON / OTIO maps)
- TimelineEditorSyncService: editor boundary coordinator (validation/conversion/mutation delegated)
- InternalTimelineMetadataEnricher: application metadata enrichment (representation-level; E1b non-semantic)
- Render/segment/cache/impact/review services: unchanged downstream consumers

## Invariants (machine-guarded)

- OUTSIDE_TIMELINE_MODULE_CANONICAL_TIMELINE_AUTHORITY_COUNT = 0
- RENDER_TIMELINE_VALIDATION_AUTHORITY_COUNT = 0
- RENDER_TIMELINE_AUTHORING_WRITE_AUTHORITY_COUNT = 0
- RENDER_TO_CANONICAL_TIMELINE_CONVERSION_AUTHORITY_COUNT = 0
- OUTSIDE_TIMELINE_MODULE_INTERNAL_TIMELINE_VALIDATION_AUTHORITY_COUNT = 0
- TIMELINE_CANONICALIZATION_AUTHORITY_COUNT = 1
- TIMELINE_SEMANTIC_DIFF_AUTHORITY_COUNT = 1
- TIMELINE_SEMANTIC_MERGE_AUTHORITY_COUNT = 1
- TIMELINE_TO_RENDER_DEPENDENCY_COUNT = 0
- No compatibility path, no dual validator, no dual writer, no V1/V2 parallel model

## Construction pipeline (frozen)

External / Editor / OTIO / Legacy
  → render interchange parse (TimelineSpecResolver / TimelineScriptParser / OTIO adapters)
  → TimelineSpec + TimelineExtensions
  → TimelineSpecImportAdapter (render boundary; mechanical mapping)
  → TimelineImportRequest (typed Timeline-owned contract)
  → TimelineImportService (timeline-module; canonical construction + E1b gate)
  → Canonical Timeline (internal-1.0 JSON)
