---
status: cleanup-report
created: 2026-06-25
scope: render-module
truth_level: current
owner: platform
---

# Platform Cleanup Sprint 030 — Remove Deprecated Branding Compatibility

## Decision

Project is pre-deployment. No external consumers depend on old branding. Remove all `@Deprecated` compatibility aliases.

## Removed (1 file)

| File | Removal |
|------|---------|
| `TimelinePlatformMetadata.java` | 11 `@Deprecated BLUEPULSE_*` aliases removed |

## Final Naming (Canonical)

| Layer | Namespace |
|-------|-----------|
| OTIO metadata keys | `platform.*` (11 keys) |
| OTIO JSON node | `"platform"` |
| JSON-LD context URLs | `https://open-media.org/xmp/...` |
| Java constants | `PLATFORM_*` (11 constants) |
| OTIOTimelineCompiler | `"platform"` namespace |

## Verification

- Zero `bluepulse`, `BLUEPULSE`, `bp`, `vizard` in any Java source or SQL schema
- Zero `@Deprecated(since = "Sprint 029")` remaining
- All affected tests pass (OTIO adapter, JSON-LD, search, marketplace)
- No documentation references to deprecated aliases

## Governance Rule

**No organization-specific branding in code, metadata keys, URLs, tests, or examples.** All namespaces use neutral platform naming (`platform`, `open-media.org`).

## Migration Notes

- Pre-Sprint 029 OTIO JSON files using `"bluepulse"` metadata key should be migrated to `"platform"`
- Pre-Sprint 029 JSON-LD contexts using `bluepulse.ai` should be migrated to `open-media.org`
- `BLUEPULSE_*` Java constants are removed — use `PLATFORM_*` equivalents
