# Marketplace publication subject contract change — V28 (Path 1b)

Date: 2026-09-25
Decision: `TYPED_ARTIFACT_MARKETPLACE_DECISION_001` → **Path 1b** (subject identity only).
Candidate: `correction/typed-artifact-convergence-v28-marketplace-1b-20260925` (parent `b774178c`).

This note is the explicit contract change record for the Marketplace publication subject. It is
required because the change is a **breaking wire and persistence identity change**, and because
three Marketplace behaviours that previously depended on the retired Media authority are narrowed.

## Contract

| | Old contract | New contract (V28) |
|---|---|---|
| Subject type | `MarketplacePublicationSubjectRef.MediaAssetSubject(MediaAssetId assetId, String version)` | `MarketplacePublicationSubjectRef.ArtifactSubject(ArtifactId artifactId, String version)` |
| Wire discriminator | `kind = "MEDIA_ASSET"` | `kind = "ARTIFACT"` |
| `version` meaning | exact Media version (`media_asset.media_version`) | pinned Artifact content digest (canonical SHA-256 hex), validated at the contract boundary |
| Persistence | `marketplace_listing.asset_id` (unique, FK → retired `media_asset_retired`), `subject_version` | `marketplace_listing.artifact_id` (unique, FK → `artifact(id)`), `subject_version` (now the content pin) |
| Identity authority | retired Media authority | Artifact authority (`ArtifactSourcePinAuthority`: identity, tenant/project scope, lifecycle, digest, media type) |
| Listing/review facts | Marketplace-owned | **unchanged** (title, description, status, workspace admission, aggregate versions, review/thread/decision/command tables) |
| Governance facts | read from Media (`classification`, `security_level`, `contains_pii`) | **not read** — no artifact-owned governance facts exist yet (see sign-off 1 and backlog) |
| Publication state | listing status **and** a mirror into `media_asset.publish_status` | listing status only (`marketplace_listing.status`, `published_at`); mirror retired |

Resolution failure mapping (unchanged in shape):

| Artifact resolution | Marketplace outcome |
|---|---|
| `UNKNOWN_ARTIFACT` | 404 `Artifact subject not found` |
| `OUT_OF_SCOPE` | 403 `Artifact subject is outside the target scope` |
| `NOT_USABLE` | 409 `Artifact subject is not usable` |
| `PIN_MISMATCH` | 409 `Artifact subject content pin changed` |
| media type outside `VIDEO/AUDIO/IMAGE/SUBTITLE` | 400 `Unsupported Artifact subject type` |

## Preserved invariants

1. Marketplace remains the sole owner of listings, reviews, threads, decisions and the command ledger.
2. Exact-subject pinning is preserved: the pinned Artifact content digest must match, and Artifact
   identity is immutable (id/tenant/content digest cannot change), so an approved review can never
   be reused for changed content.
3. Public visibility still requires the Marketplace `PUBLISHED` state, a resolvable in-scope
   Artifact pin and the workspace relationship.
4. Historical rows stay inert: `admitted_at IS NULL` rows keep their `legacy_snapshot` byte-for-byte
   and gain no runtime authority.

## Not migrated (explicitly out of scope for 1b)

Artifact-owned governance/publication/version facts are **not** invented here; the typed-schema
pre-V18 baseline (which would be needed to read V18+ tables from canonical code) is unchanged. Both
remain on the backlog. V21 also adds marketplace column drift to that same typed-schema baseline.
