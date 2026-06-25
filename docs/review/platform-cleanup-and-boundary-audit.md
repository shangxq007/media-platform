---
status: cleanup-report
created: 2026-06-25
scope: render-module + platform-app + test
truth_level: current
owner: platform
---

# Platform Cleanup Sprint 029 — Branding Neutralization & Boundary Audit

## Branding Neutralization

### Changes Applied

| Component | Before | After |
|-----------|--------|-------|
| OTIO metadata namespace | `bluepulse.*` | `platform.*` |
| OTIO metadata JSON key | `"bluepulse"` | `"platform"` |
| OTIO constants | `BLUEPULSE_PROJECT_ID` etc. (11) | `PLATFORM_PROJECT_ID` etc. (11) |
| JSON-LD context URLs | `https://bluepulse.ai/xmp/...` | `https://open-media.org/xmp/...` |
| OTIOTimelineCompiler | `"bluepulse"` namespace | `"platform"` namespace |

### Deprecated Aliases (Backward Compatible)

All 11 `BLUEPULSE_*` constants retained as `@Deprecated` aliases pointing to `PLATFORM_*` equivalents. No existing code breaks.

### No Code Branding Found

- No `bluepulse`, `bp`, or `vizard` in Java packages, SQL schema, or source files beyond the metadata keys listed above
- Branding was limited to OTIO metadata namespace + JSON-LD context URLs
- All documentation references to `bluepulse` are in config guides (HashiCorp Vault setup, deployment docs) — left as-is (not source code)

## Shared Kernel Audit

### Contents Verified

| Category | Examples | Status |
|----------|---------|--------|
| **Domain Events** | `RenderJobCreatedEvent`, `AssetPublishedEvent`, `TimelineMergedEvent` | ✅ Correct |
| **Value Objects** | `EventTypeDescriptor`, `CapabilityStability` | ✅ Correct |
| **Enums** | `FlowStatus`, `InvocationStatus` | ✅ Correct |
| **Cross-Module Contracts** | `CostReservationPort`, `AssetDownloadUrlPort`, `StorageUriReferenceContributor` | ✅ Correct |
| **Registry Contracts** | `EventTypeRegistry`, `ExtensionProviderRegistry`, `HookPointRegistry` | ✅ Correct |
| **Business Services** | None found | ✅ Clean |
| **Repositories** | None found | ✅ Clean |
| **Controllers** | None found | ✅ Clean |
| **Task Handlers / Dispatchers** | None found | ✅ Clean |

### Boundary Violations: None

Shared kernel contains only contracts, events, enums, and value objects — no business logic, persistence, or API code.

## Module Responsibility Matrix

| Module | Owns |
|--------|------|
| **shared-kernel** | Domain events, value objects, enums, cross-module ports/contracts |
| **outbox-event-module** | Outbox persistence, dispatch, routing, coordination (platform_job/task) |
| **render-module** | Timeline Git, OTIO adapter, Asset Registry, semantic metadata, search, marketplace listing |
| **notification-module** | Notification templates, delivery, preferences, inbox |
| **audit-compliance-module** | Audit record persistence, event-driven audit logging |
| **platform-app** | REST controllers, Flyway migrations, Spring configuration |

## Files Modified

| File | Change |
|------|--------|
| `TimelinePlatformMetadata.java` | 11 `BLUEPULSE_*` → `PLATFORM_*` constants (deprecated aliases retained) |
| `OpenTimelineioAdapter.java` | All BLUEPULSE references → PLATFORM; JSON node `"bluepulse"` → `"platform"` |
| `OTIOTimelineCompiler.java` | `"bluepulse"` → `"platform"` namespace |
| `AssetJsonLdExporter.java` | URLs: `bluepulse.ai` → `open-media.org` |
| `AssetRegistryService.java` | URLs: `bluepulse.ai` → `open-media.org` |
| `AssetJsonLdExporterTest.java` | Updated URL assertions |
| `OpenTimelineioAdapterMetadataTest.java` | Updated constant + string assertions |
| `OTIOTimelineCompilerTest.java` | `"bluepulse"` → `"platform"` |
| `CaptionedVideoExportE2ETest.java` | `"bluepulse"` → `"platform"` |
| `CaptionedVideoExportSmokeTest.java` | `"bluepulse"` → `"platform"` |

## Tests

All branding-affected tests pass (OpenTimelineioAdapterMetadataTest, AssetJsonLdExporterTest, OTIOTimelineCompilerTest).

## Migration Notes

- `BLUEPULSE_*` constants are `@Deprecated(since = "Sprint 029", forRemoval = true)` — migrate to `PLATFORM_*` equivalents
- OTIO JSON files using `"bluepulse"` metadata key should migrate to `"platform"` 
- JSON-LD contexts using `bluepulse.ai` should migrate to `open-media.org`
- No `bluepulse`, `bp`, or `vizard` remains in any Java source file, SQL schema, or non-documentation resource
