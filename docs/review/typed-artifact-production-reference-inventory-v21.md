# Typed Artifact production reference inventory — V21

Date: 2026-09-25

## Classification

| Area | Remaining reference | Classification | Disposition |
|---|---|---|---|
| Flyway V1–V20 and generated jOOQ historical tables | `media_asset*`, `media_stream`, probe/link records | historical migration-only | retained for forward migration evidence; no default production bean |
| Timeline canonical/source JSON fields | `mediaAssetId` in legacy source bindings | historical/logical metadata | retained for old revision decoding; validator bean disabled by default |
| Raw upload, asset registry/search, marketplace, media persistence, render operation adapters | MediaAsset contracts and repositories | reachable legacy authority candidate | all Spring beans/routes explicitly fenced behind `legacy-media-disabled`; must be replaced by Artifact-owned ports in the next migration pass |
| Tests and generated schema fixtures | MediaAsset names | test/generated fixture | not production loaded by guard |

The guard rejects any default-profile Spring bean containing MediaAsset identity,
query, repository, probe, or persistence references unless it carries the
`legacy-media-disabled` profile. This is a fail-closed retirement fence; it is
not a claim that the underlying logical media flows have been reimplemented as
Artifact flows.
