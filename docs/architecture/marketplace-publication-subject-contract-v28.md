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

## Sign-off decisions (decision TYPED_ARTIFACT_MARKETPLACE_DECISION_001, Path 1b)

| # | Item | Decision recorded by this candidate |
|---|---|---|
| 1 | Governance eligibility (`contains_pii`, classification, security level) | **Retired with the media authority; not evaluated.** No artifact-owned governance facts exist and inventing them is out of 1b scope. Public visibility and publish admission are decided by Marketplace-owned state plus the Artifact pin. Restoring the pre-V28 policy requires 1a (artifact-owned governance facts) — backlog. |
| 2 | Media-version equality → digest pin | **Replaced by the immutable Artifact content pin.** `ArtifactSubject.version` is the canonical Artifact content digest, validated at the contract boundary and compared against the Artifact authority's recorded digest. Because Artifact identity/digest cannot change under one id, an approved review can never be reused for changed content. |
| 3 | Publication mirroring | **Retired.** Publication state is Marketplace-owned (`marketplace_listing.status`, `published_at`). The Marketplace no longer reads or writes `media_asset.publish_status`; artifact/listing withdrawal is listing-only and never mutates the subject. |
| 4 | Wire change breaking | **Accepted.** `kind: "MEDIA_ASSET"` → `"ARTIFACT"`; `subject.assetId{value}` → `subject.artifactId{value}`; `managedByAsset` → `managedByArtifact`; `publicationFact(..., assetId)` → `(..., artifactId)`; publish-status/review-summary response keys `assetId` → `artifactId`; marketplace/asset-publish path templates `{assetId}` → `{artifactId}` (path shapes unchanged); `docs/api/openapi-preview-current.json` updated (`MediaAssetSubject` schema removed, `ArtifactSubject` published). |
| 5 | Historical outbox/audit payloads | **Retained as evidence, never rewritten.** The decoder accepts only `kind=ARTIFACT`; a historical `kind=MEDIA_ASSET` payload no longer decodes and is handled fail-closed by the outbox dispatcher (dead-letter), consistent with the V7 precedent of explicit retirement instead of reconstruction. Audit records embed the subject textually and are untouched. |
| 6 | Production row volume / migration strategy | **Single transactional migration with a fail-closed pre-flight.** V21 aborts (`MARKETPLACE_SUBJECT_UNMAPPED`) if any listing lacks an Artifact identity derived from `artifact_media_details.legacy_media_asset_id`; the identity backfill is one `UPDATE ... FROM` executed with the V7 immutability fence disabled and re-enabled, so no admitted row is silently dropped or rewritten beyond the subject id. Row counts are not visible from the repository; a large production table may need the same statement executed in reviewed batches. |

### Fence scope change (recorded explicitly)

`MarketplaceService` and `MarketplaceStore` were fenced by V21 **because their subject depended on the
retired media authority**. With that dependency removed they are default-profile beans, which is what
makes `MarketplaceApi` satisfiable. No legacy media authority fence was removed: `MediaAssets`,
`MediaAssetQueries`/`MediaStreamQueries`, their jOOQ implementations and every other retired-media
consumer remain fenced or deleted, and the convergence guard still reports 0 findings.

### Independent-review acknowledgements (amendment 1)

The independent review of `63df6bf0`
(`Documents/workspace/audit-runs/TYPED_ARTIFACT_V28_MARKETPLACE_1B_INDEPENDENT_REVIEW_20260925/`)
accepted the runtime, migration, fence, H8 and guard evidence and required the OpenAPI subject schema
body to match the runtime contract (fixed in amendment 1). The review's remaining findings are
acknowledged here verbatim in scope:

| ID | Acknowledgement |
|---|---|
| **L2 — governance narrowing** | With sign-off 1 accepted, **any resolvable, usable Artifact can be published**, including content that the retired media authority would have blocked via `contains_pii` / classification / security level. The pre-V28 eligibility gate is gone by decision and can only return with backlog item 1a (artifact-owned governance facts). This is a product/compliance exposure and is acknowledged, not mitigated by this amendment. |
| **L3 — retired media authorization precondition** | `canPublish` no longer calls the media-owned `requireRegistrationScope` precondition (removed with the media authority). The actual publish decision remains authorized by the Marketplace `marketplace.publish` project permission and the Artifact pin; the removed check was a media-owned precondition, and this was not itemized in the six sign-offs above — recorded here as a documentation gap now closed. |
| **L4 — outbox event version unchanged** | Marketplace outbox event types remain `version = 1` even though the subject payload schema changed (`kind` value and the subject field name). Historical payloads stay as evidence (sign-off 5) and consumers must branch on `kind`; the event version was deliberately not bumped. Operators integrating against the outbox should treat the marketplace subject payload change as breaking regardless of `event_version`. |
| **L5 — test-scope reduction** | The amendment lineage removes **36 assertion lines** across the retargeted marketplace test files. Those lines asserted retired behaviour (media publication mirror, search-projection publication state, media-owned governance eligibility, media-version equality) and were replaced by canonical equivalents (listing-owned publication state, Artifact pin/lifecycle rejection, fenced-reindex expectations). Coverage of the retired behaviour is intentionally gone with the retirement and is acknowledged explicitly rather than silently. |
