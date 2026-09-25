# TimelineSourceValidation contract change — V27 (Path 1b)

Date: 2026-09-25
Decision: `TYPED-ARTIFACT-DECISION-001` → **Path 1 + 1b** (artifact pin + scope validation).
Candidate: `correction/typed-artifact-convergence-v27-path1b-20260925` (parent `640a4cbd`).

This note records a **deliberate narrowing** of the published Timeline contract
`com.example.platform.timeline.api.composition.TimelineSourceValidation`. The change is explicit
here because the port interface file itself is frozen by the H8 byte attestation and therefore
cannot carry the documentation inline.

## Contract

| | Old contract | New contract (V27) |
|---|---|---|
| Meaning | Source **stream** validity: the binding's Media asset exists in the target tenant/project, the referenced stream belongs to that asset, and the stream kind is compatible with the target track | Source **Artifact pin + scope** validity: the pinned Artifact identity exists, is inside the target tenant/project scope, is usable, and its recorded content digest equals the pinned digest |
| Authority | Retired Media authority (`MediaAssets` / `MediaAssetQueries` / `MediaStreamQueries`, all retired in V18–V21) | Artifact authority (`artifact` identity, tenant/project scope, `content_digest`, lifecycle) |
| Implementation | `TimelineSourceReferenceValidator` (media-backed, now fenced) | `ArtifactPinTimelineSourceValidator` (scope-bound Artifact pin resolution) |
| Resolution capability | — | `com.example.platform.artifact.app.ArtifactSourcePinAuthority` (new, Artifact-owned) |
| Context-free overload | Structural source-reference check | **Fails closed**: Artifact pin resolution is scope-bound, so a binding without tenant+project yields an explicit violation instead of a structural-only pass |
| Stream-level validity | Enforced | **Not enforced by this port** (backlog) |
| Target track compatibility (`expectedTrackType`) | Enforced against stream kind | **Not enforced by this port** (stream-level data; backlog) |

Failure reporting is unchanged in shape: `ValidationResult(false, violations)` with one explicit
violation naming the outcome (`unknown` / `outside target project scope` / `not usable` /
`content digest mismatch`). The consuming H8 pipeline (`TimelineMediaClipOperationService`)
continues to translate that into `TimelineOperationException.Code.SOURCE_REFERENCE_INVALID`.

## Implementation exclusivity

| Profile | Active implementation |
|---|---|
| default (production, test) | `ArtifactPinTimelineSourceValidator` (`@Profile("!legacy-media-disabled")`) |
| `legacy-media-disabled` | `TimelineSourceReferenceValidator` (unchanged, still fenced) |

Exactly one `TimelineSourceValidation` bean is registered per profile. The retired implementation
is left byte-identical because it is inside the H8-attested universe; it is inactive by default
and its V21 fence is preserved. It is not a compatibility layer: nothing routes to it in the
default graph.

## Retired-authority independence

`ArtifactSourcePinAuthorityService` reads only the canonical `artifact` read projection
(`ArtifactCatalogService.findArtifact(tenantId, id)`: id, tenant, project, digest, status). It
does not reference `MediaAssets`, `MediaAssetQueries`, `MediaStreamQueries`, the legacy media
repositories, storage placement or the render-job scope. The typed-schema module's pre-V18
baseline therefore does not block this path: every column used exists in the generated
pre-V18 `artifact` table.

## Backlog (explicitly unresolved)

1. **Stream-level validity** — `MediaStreamId` membership and stream-kind compatibility are no
   longer validated before canonical application. Restoring them requires Artifact-owned stream
   facts (V19 collapsed `media_stream` into a reduced `tracks` JSONB without stream identity) and,
   if still required, a canonical write path for those facts.
2. **Media identity validity** — legacy `mediaAssetId` / `mediaStreamId` carried by
   `MediaStreamSourceBinding` are treated as non-authoritative references; whether they must be
   resolvable at all is an open question of the Artifact-native source model.
3. **typed-schema pre-V18 baseline** — the jOOQ generator baseline still precedes V18, so
   V18+ tables (for example `artifact_media_details`, which holds the legacy-media mapping) remain
   unavailable to canonical code. Required for backlog item 1, not for this contract.
