---
document_id: STUDIO_CREATIVE_SCENE_PREVIS_BOUNDED_ARCHITECTURE_CONTRACT_V1
artifact_type: BOUNDED_ARCHITECTURE_CONTRACT
authority_class: NORMATIVE_CANDIDATE
lifecycle_state: PROPOSED
acceptance_state: READY_FOR_HERMES_FREEZE_REVIEW
owner: studio
retention_class: PROJECT_LIFETIME
base_sha: bd919f958bea79e57fe7fcb1cf1396eca96a0e9d
base_tree: 9edb4c10d15e567bff7300190f156f21cf2d7cf0
run_marker: STUDIO_V3_S0_SINGLE_WRITER_RUN_1
---

# Studio creative scene/previs bounded architecture contract v1

## 1. Status, scope, and sole authority

This document is the sole Studio S0 architecture contract candidate. It freezes
the Studio creative-semantics and Director Preview boundaries for later S1/S2/S3
implementation. It does not freeze a Git candidate, create an implementation,
or claim runtime adoption. Hermes verification and commit are still required.

S0 selects one bounded modular-monolith direction named `studio-module`. S0 does
not add that Gradle module, source, API, table, migration, generated type,
frontend behavior, provider integration, or runtime configuration. The companion
`studio-api-dependency-gap-ledger-v1.json` is the sole machine-readable gap
authority. No separate Director Preview or product contract is authorized.

This contract was independently derived from the task packet, the exact accepted
base above, and the current repository. Historical Studio drafts, collision
worktrees, and draft archives are not inputs.

## 2. Frozen authority laws

The following ownership laws are normative:

1. `STUDIO_OWNS_CREATIVE_SEMANTICS`.
2. `TIMELINE_OWNS_FINAL_MEDIA_COMPOSITION`.
3. `ARTIFACT_OWNS_IMMUTABLE_MATERIALIZATION`.
4. `MEDIA_OWNS_MEDIA_SOURCE_TRUTH`.
5. `OPERATION_OWNS_TRANSACTIONAL_SEMANTIC_APPLICATION_BOUNDARY`.
6. `RENDER_OWNS_PRODUCTION_RENDER_PLANNING`.
7. `PROVIDER_OWNS_HOW`.
8. `RUNTIME_OWNS_WHERE_AND_WHEN`.
9. `WORKFLOW_OWNS_PROCESS`.

Consequences:

- `CREATIVE_ARTIFACTS_ARE_OPTIONAL_V1`: a Screenplay, Scene, Shot, ShotPlan,
  DirectorIntent, CameraPlan, Storyboard, or ShotScene is valid without a
  materialized Artifact unless its own field contract requires a pin.
- `SCREENPLAY_IS_NOT_TIMELINE_AUTHORITY_V1`.
- `SHOT_IS_NOT_TIMELINE_CLIP_V1`.
- `SHOTPLAN_IS_NOT_RENDERPLAN_V1`.
- `STORYBOARD_IS_NOT_TIMELINE_V1`.
- `STORYBOARD_PANEL_IS_NOT_VIDEO_FRAME_AUTHORITY_V1`.
- `SHOTSCENE_IS_CANONICAL_SPATIAL_PLANNING_NOT_PROVIDER_SCENE_GRAPH_V1`.
- `DIRECTOR_INTENT_IS_SEMANTIC_WHAT_NOT_PROVIDER_HOW_V1`.
- `CAMERA_PLAN_IS_PROVIDER_AGNOSTIC_V1`.
- `OPENUSD_IS_DERIVED_SCENE_REPRESENTATION_NOT_DOMAIN_AUTHORITY_V1`.
- `BLENDER_IS_REPLACEABLE_PREVIS_PROVIDER_V1`.
- `OMNIVERSE_IS_OPTIONAL_PRODUCT_PROVIDER_NOT_CANONICAL_AUTHORITY_V1`.
- `PROVIDER_NATIVE_SCENE_IDS_ARE_NOT_CANONICAL_IDS_V1`.
- `DIRECTOR_PREVIEW_IS_DERIVED_PLANNING_PRODUCT_NOT_FINAL_MEDIA_COMPOSITION_V1`.
- `CREATIVE_TO_MEDIA_LOWERING_GOES_THROUGH_OPERATION_MODEL_V1`.
- `CREATIVE_TO_TIMELINE_DIRECT_MUTATION_IS_FORBIDDEN_V1`.
- `NO_UNIVERSAL_ASSET_GOD_OBJECT_V1`.
- `OPERATIONS_TARGET_SEMANTIC_SCOPE_NOT_STORAGE_OBJECTS`.

## 3. Bounded context and dependency direction

`studio-module` will own Studio aggregates, typed identities, deterministic
versioning, semantic comparison, and Studio application policies. It may depend
only on stable owner-facing contracts selected in later authorized stages. It
must not make Media, Artifact, Timeline, Render, Workflow, Operation, Capability,
Provider, Worker Fabric, Commercial, or Composite Resource depend on Studio
domain types.

The lawful conceptual flow is:

```text
frontend projection
  -> Studio application command/query
  -> Operation-owned transactional application boundary
  -> Studio immutable version commit
  -> optional explicit lowering request
      -> Timeline-owned revision application (final composition)
      -> Render-owned production planning (only from valid render inputs)
      -> Provider-owned derived lowering (previs HOW)
      -> Runtime/Worker Fabric placement and execution (WHERE/WHEN)
      -> Artifact-owned immutable output and provenance
```

Workflow may orchestrate these steps but cannot become the source of Studio
semantics. Provider or runtime success cannot advance Studio semantic history.
An output/provenance association is derived evidence, not a Studio version.

The current Operation model is intentionally not treated as sufficient: its
targets, definitions, instances, and plan are Timeline-oriented. The Operation
owner must canonicalize a typed, domain-extensible semantic target/application
contract before Studio lowering. Studio must not add a private generic command
bus, raw action string, JSON-patch bypass, or direct Timeline repository call.

## 4. Typed identity and reference rules

Every Studio identity is a validated opaque value, never an ordinal, label,
array index, provider document ID, storage coordinate, URI, filename, digest,
or database row location. At minimum later stages require:

- `ScreenplayId`, `ScreenplayVersionId`, and `ScreenplayElementId`;
- `SceneId` and `SceneVersionId`;
- `ShotId` and `ShotVersionId`;
- `ShotPlanId` and `ShotPlanVersionId`;
- `DirectorIntentId` and `DirectorIntentVersionId`;
- `CameraPlanId` and `CameraPlanVersionId`;
- `StoryboardId`, `StoryboardVersionId`, and `StoryboardPanelId`;
- `ShotSceneId`, `ShotSceneVersionId`, and `SceneElementId`;
- `PrevisRequestId` and `DirectorPreviewId` for derived-product tracking.

Every aggregate is scoped by an exact tenant and Project reference. The current
repository's Project entity uses a string ID and does not expose the accepted
Workspace-to-Project relation required by the frontend foundation. Until the
Identity owner closes that gap, Project-to-Studio resolution fails closed; Studio
must not synthesize a Project, Workspace, membership, or scope relation.

Cross-owner references are typed immutable pins:

- an Artifact image/output pin contains `ArtifactId` and exact `ContentDigest`;
- a media source pin contains `MediaAssetId` plus the exact owner-defined media
  version and, when bytes are required, an Artifact/content pin;
- a Timeline reference contains its exact Timeline revision identity and digest;
- a Studio version pin contains aggregate kind, aggregate ID, exact version ID,
  schema version, and semantic content digest;
- a future Composite Resource component pin contains the future owner-defined
  resource/facet/component identities plus its exact immutable version/digest.

No pin contains a bucket, object key, filesystem path, signed URL, provider
handle, runtime address, or mutable `latest` reference. Reads resolve exact
historical pins and fail closed on absence, scope mismatch, type mismatch, or
digest mismatch.

## 5. Screenplay and narrative Scene

### 5.1 Screenplay authority

A Screenplay is Studio-authored narrative semantics, not a Timeline document.
Each immutable Screenplay version contains its identity, Project scope, parent
version, schema version, semantic digest, and an ordered sequence of structured
elements. The v1 element vocabulary is closed and typed:

- scene heading, with interior/exterior intent, location text, and time-of-day
  semantics;
- action;
- character cue, linked by stable semantic reference when a character exists;
- dialogue;
- parenthetical;
- transition intent;
- note, explicitly non-production and non-Timeline authority.

Every element has a stable `ScreenplayElementId`. Authored order is semantic and
digest-bearing. Formatting offsets, editor selection, page breaks, pagination,
line wrapping, and provider document IDs are projections and never semantic
identity. A parser/importer must produce typed elements and an explicit
diagnostic set; it may not retain an unvalidated text/JSON blob as canonical
Screenplay semantics.

### 5.2 Narrative Scene

A narrative Scene is a stable creative identity. `SceneId` survives renumbering,
reordering, heading edits, screenplay insertion/deletion, and provider export.
Display ordinal such as “Scene 12” is a versioned projection and not identity.

A Scene version pins the exact Screenplay version and heading element from which
its current narrative boundary is authored. Its semantics may include synopsis,
narrative purpose, participating character/resource semantic references, and
continuity notes. It owns neither spatial transforms nor a provider scene graph;
those belong to ShotScene planning or provider-derived execution respectively.

## 6. Shot, ShotPlan, DirectorIntent, and CameraPlan

### 6.1 Shot

A Shot is a creative identity for a planned visual beat. It is distinct from a
TimelineClip, take, MediaAsset, Artifact, RenderPlan node, frame, camera object,
or placement. A Shot version contains a stable `ShotId`, exact Scene version
pin, description, subject/action intent, continuity intent, and optional exact
DirectorIntent/CameraPlan pins. Duration may be a planning intent expressed with
exact `MediaTime`; it is not a Timeline placement or final duration authority.

### 6.2 ShotPlan

A ShotPlan version must pin exactly one Screenplay version and exactly one Scene
version whose lineage and Project scope agree. It contains an ordered list of
exact Shot version pins. The ordered list is semantic and duplicates are
forbidden unless a future explicit typed repetition construct is authorized.
Mutable-latest Shot, Scene, or Screenplay references are forbidden.

ShotPlan owns creative coverage and ordering inside narrative planning. It does
not own tracks, clips, edit points, transitions, media source truth, output
requirements, provider selection, executable tasks, or RenderPlan/RenderGraph.

### 6.3 DirectorIntent

DirectorIntent expresses semantic WHAT: narrative emphasis, emotional tone,
staging/blocking intent, subject priority, reveal/occlusion intent, pacing,
continuity constraints, camera-movement intent, lighting mood, and explicit
creative constraints. Values must be typed or from a schema-versioned closed
vocabulary; free text may annotate intent but cannot smuggle executable commands.

DirectorIntent must not contain provider names, executable names, scripts,
commands, flags, node graphs, shader graphs, model/vendor identifiers, device
requirements, provider-native scene IDs, storage paths, or runtime placement.

### 6.4 CameraPlan

CameraPlan is provider-neutral authored camera semantics. It contains stable
camera-plan identity; projection type; framing and target intent; exact transform
or look-at semantics; focal length or field-of-view according to one unambiguous
typed variant; sensor/aperture/focus/exposure intent where authored; movement
path as typed keyframes; and exact Shot/ShotPlan/DirectorIntent pins.

Mutually derived optics may not be stored as competing authorities. The chosen
variant is canonical and other values are projections. CameraPlan contains no
provider camera type, object name, node path, renderer settings, or device data.

## 7. Storyboard and Panel

A Storyboard version is optional visual planning. It pins the exact ShotPlan
version it visualizes and contains ordered Panels. Each Panel has a stable
`StoryboardPanelId`, exact Shot version pin, composition annotation, optional
exact CameraPlan pin, and an immutable image pin containing both `ArtifactId`
and `ContentDigest`. Missing image materialization is allowed when the Panel is
explicitly planned/unmaterialized; a present image requires both values and
owner validation.

A Panel image is a planning representation. Panel order does not create a
Timeline. A Panel is never a video-frame identity, a take, a media stream, a
TimelineClip, or proof that a preview/render completed. Panel annotations do not
mutate Artifact metadata or Media source truth.

## 8. ShotScene spatial-planning aggregate

A ShotScene version is the canonical bounded spatial plan for an exact ShotPlan
version. It is not a universal Scene asset, generic resource graph, provider
scene graph, OpenUSD stage, Blender file, Houdini network, Omniverse document,
RenderGraph, or executable plan.

It contains:

- exact ShotPlan, Screenplay, and Scene version pins;
- stable `SceneElementId` values with a closed kind vocabulary such as character,
  prop, environment reference, light intent, camera reference, and marker;
- typed immutable resource pins rather than embedded universal asset records;
- parent-child spatial relationships that form a validated acyclic forest;
- local transforms under the frozen spatial convention;
- provider-neutral camera references and CameraPlan pins;
- typed lighting/environment/ambience intent;
- typed motion/blocking paths and exact temporal samples where authored;
- semantic constraints and diagnostics.

SceneElement identity is Studio identity, not storage or provider identity.
Resource metadata is not copied into SceneElement. Lighting/environment/motion
are semantic intent, never scripts or provider commands. Cross-element
references resolve within the exact version and fail closed when dangling,
cyclic, scope-incompatible, or non-finite.

## 9. Spatial and numeric determinism

Studio v1 uses a right-handed coordinate system: +X right, +Y up, and the
canonical camera forward direction is -Z. Linear units are meters. Angles use
degrees as canonical finite decimal values unless a field explicitly defines a
dimensionless ratio. No implicit unit conversion is permitted.

Canonical decimal rules are:

1. Values are finite mathematical decimals; NaN, infinities, binary-float
   payloads, locale-specific text, and exponent-form authority are forbidden.
2. Canonical encoding is a base-10 string with no leading `+`, no exponent,
   no redundant leading/trailing zero, and `-0` normalized to `0`.
3. Each field defines a maximum scale and range; out-of-range or over-scale
   values fail closed rather than round implicitly.
4. Vectors encode components in fixed x/y/z order.
5. Rotation is a provider-independent unit quaternion in fixed x/y/z/w order.
   Input is normalized deterministically using the schema-versioned decimal
   algorithm and tolerance. Zero/invalid norm fails closed. The canonical sign
   makes w positive; when w is zero, the first nonzero x/y/z component is
   positive. Equivalent q and -q therefore have one representation.
6. Transform composition order is fixed as scale, then rotation, then
   translation in the documented parent coordinate space. Non-uniform scale is
   allowed only in fields that explicitly opt in; shear is forbidden in v1.

Providers may convert coordinates/units privately but must preserve provenance
for the conversion. Converted provider values never enter Studio semantic
digests.

## 10. Immutable versioning, CAS, serialization, and diff

### 10.1 Linear immutable history

Each Studio aggregate has an independent, deterministic, linear version stream.
Every successful semantic change appends exactly one immutable version with an
exact parent; version zero does not exist. v1 has no branches, merge commits,
last-write-wins, in-place semantic mutation, or mutable-latest pinning.

Commands bind aggregate ID, exact expected head version, expected head digest,
and idempotency identity. Commit uses compare-and-set under one database
transaction. A head mismatch returns a typed conflict without writing. An
idempotent replay with identical canonical input returns the original result;
the same key with different input fails closed. Reads by exact version remain
available according to retention policy.

### 10.2 Canonical serialization and digest

Each aggregate schema version owns one canonical serializer. It emits UTF-8
canonical JSON with lexicographically ordered object member names, exact closed
enum spellings, canonical decimal strings, normalized Unicode NFC strings,
explicit booleans, and no insignificant whitespace. Optional absence is omitted;
`null` is not an alternate spelling. Sets are sorted by their canonical element
encoding. Semantically ordered lists retain authored order. Maps with open or
provider-defined keys are forbidden as canonical domain semantics.

The semantic digest is SHA-256 over the canonical bytes and is represented as
lowercase 64-character hexadecimal. Identity, schema version, parent/version
lineage rules, and all authored semantic fields participate as defined per
aggregate. Created-at/by, request/trace/idempotency metadata, database values,
storage location, materialization state, Artifact replica details, provider
selection, provider-native IDs, runtime/worker/device/lease state, execution
timestamps, logs, metrics, and derived provenance are excluded from semantic
content digests.

### 10.3 Semantic diff and comparison

Studio comparison is domain-semantic, not text diff, JSON Patch, or database
row diff. It compares two exact versions of the same aggregate and emits typed
changes addressed by stable identity: field replacement, element add/remove,
authored reorder, reference repin, spatial-parent change, transform change, and
typed constraint change. List index alone is never a target. Diff ordering is
deterministic. Cross-aggregate comparison fails closed. A future API must expose
this without borrowing Timeline's Timeline-specific diff types.

### 10.4 Clean-forward evolution

New schemas are append-forward. A decoder accepts only explicitly supported
schema versions; migration is a pure deterministic conversion that produces a
new immutable version under the new schema. No compatibility constructor,
default-to-current reinterpretation, silent field drop, provider-dependent
migration, or rewrite of historical bytes is allowed. Retired fields and
semantics are removed from new canonical writers rather than kept as dual
authority.

## 11. Director Preview derived-product boundary

Director Preview is a derived planning product inside this sole contract. It
allows an authorized user to request low-fidelity visual evaluation of an exact
Shot, ShotPlan, Storyboard, CameraPlan, or ShotScene version. A request binds all
Studio version/digest pins, desired typed preview role, and non-semantic quality
constraints. It never reads mutable latest.

The application status model distinguishes accepted, rejected, queued/running,
succeeded, failed, and cancelled without treating provider/runtime status as
Studio semantic history. A successful result references immutable Artifact
output(s), exact content digest(s), and provenance linking request, input pins,
provider binding, runtime/execution evidence, and materialization. Output access
uses Artifact/Storage application boundaries; no storage coordinate appears in
Studio domain or durable frontend state.

Director Preview does not:

- create or mutate Timeline tracks, clips, revisions, or edit decisions;
- establish final media composition, frame authority, take selection, or final
  duration;
- become a ShotPlan, RenderPlan, Workflow, provider graph, or production record;
- make a generated image/video a canonical character, Scene, or Shot;
- allow provider-native IDs to replace Studio IDs;
- treat completion as authorization to publish or render production media.

Production rendering remains a Render-owned plan derived from valid canonical
render inputs. When a user explicitly lowers creative intent toward a Timeline,
the request crosses the Operation-owned application boundary and Timeline alone
commits the new revision. Studio observes the returned exact revision; it does
not transact against Timeline tables.

## 12. Provider, execution, and runtime boundaries

Studio canonical semantics are neutral to Blender, Houdini, OpenUSD
implementation details, BMF internals, FFmpeg commands, specific AI/TTS vendors,
generation models, render engines, devices, workers, and clouds.

- Blender is a replaceable future previs provider.
- Houdini is a replaceable future procedural/previs provider.
- OpenUSD is an optional derived/interchange scene representation produced from
  an exact ShotScene version. A USD prim path or identifier is provider-native
  provenance, never Studio identity.
- Omniverse may be an optional product/provider integration over derived
  representations. It is not canonical authority.
- AI image/video/voice generation is provider-owned HOW behind typed capability
  requirements and policy. Model/vendor names are provenance, not authored
  Studio semantics.

Provider selection and native lowering occur only after canonical Studio input
is frozen. Provider outputs must retain exact input pins and provider binding.
Worker Fabric/runtime owns eligibility, reservation, assignment, lease, device,
sandbox, command execution, and lifecycle. Studio neither selects a worker nor
declares provider-native runtime dependencies.

The current BMF module is fail-closed for native lowering/runtime adaptation and
is not a Studio dependency. Studio must not import BMF types, contain a BMF
graph/filtergraph, or call BMF internals. Any later BMF use is private provider
lowering beneath shared provider/runtime boundaries.

## 13. Composite Resource compatibility constraint

Composite Resource Foundation (`CR-0`) is a parallel owner lane. Studio S0 does
not implement it, define its generic graph, or create a Studio-private substitute.
Studio semantics must remain compatible with the future relation:

`CompositeResource -> SemanticFacet -> Typed Component Reference`

Illustrative future facet vocabulary includes
`studio.character.profile`, `studio.character.personality`,
`studio.character.voice`, `studio.character.visual`,
`studio.character.spatial`, `studio.character.motion`,
`studio.scene.environment`, `studio.scene.characters`, `studio.scene.props`,
`studio.scene.lighting`, `studio.scene.camera`, and `studio.scene.ambience`.
These names are compatibility examples only, not implemented authorities.

Forbidden designs include:

- a universal `CharacterCard` god object;
- a universal Scene/asset superclass;
- a JSON bag as character semantics;
- provider-specific Composite Resource fields;
- component identity derived from storage location;
- a Studio-private generic resource/facet/component graph;
- copying component semantics into ShotScene to avoid exact pins.

Operation targeting must remain compatible with future `WholeResourceTarget`,
`FacetTarget`, and `ComponentTarget`. One logical Studio operation may expand
into multiple facet-specific operations at an Operation-owned boundary. It may
not target a storage object or provider scene node. DirectorIntent and ShotScene
contain no provider commands.

## 14. Owner boundary matrix

| Concern | Canonical owner | Studio may do | Studio must not do |
|---|---|---|---|
| Creative narrative/spatial semantics | Studio | author/version/compare exact Studio aggregates | turn provider output into identity |
| Source media | Media | hold typed exact source pins | copy probe/stream/storage truth |
| Immutable bytes/provenance | Artifact | validate pins and reference results | mutate Artifact or own replica/storage state |
| Final composition/revision | Timeline | request explicit Operation-based lowering | write Timeline state/tables or equate Shot with Clip |
| Transactional semantic application | Operation | submit typed creative intent after canonical extension | private command bus, JSON patch, repository bypass |
| Production planning | Render | request render from valid owner inputs | make ShotPlan/ShotScene a RenderPlan |
| Provider mechanics | Provider | declare provider-neutral requirements | scripts, commands, native graph/IDs in semantics |
| Placement/execution | Runtime/Worker Fabric | observe safe status/provenance | select devices/workers, own leases/tasks |
| Process | Workflow | participate as typed process steps | encode creative truth in workflow state |
| Effective access/commercial admission | Entitlement/Policy/Quota/Capability owners | request decision and fail closed | infer access, plan, quota, or runtime availability |
| Generic resource composition | Composite Resource owner | use future exact facet/component pins | create a generic Studio resource graph |
| UI state | Frontend | project safe canonical data and transient interaction | persist or synthesize canonical truth |

## 15. Persistence and modularity direction

Later implementation uses PostgreSQL with Studio-owned tables and jOOQ adapters
inside `studio-module`. DDL remains in the repository's single central Flyway V1
authority and generated jOOQ types remain in `typed-schema-module`; an
authorized schema task must update those authorities together. S0 creates no
DDL or generated files.

Studio table names will use a bounded `studio_` namespace and explicit foreign
keys for local aggregate/version/head relations. External-owner identities are
stored as typed reference values plus exact version/digest evidence, not foreign
keys into another module's private table unless that owner explicitly exposes a
canonical database relationship. Canonical payload/digest and relational
indexes must agree; database JSON is not semantic authority.

Version append, idempotency record, and head CAS are one explicit jOOQ/PostgreSQL
transaction. Reads are tenant/Project scoped at the database predicate. S1 must
add Spring Modulith metadata and tests proving no reverse owner dependency, no
unexpected package access, and no new allowlisted architecture debt. It must not
hide a boundary inversion in `ModularityTest.ALLOWED_VIOLATIONS`.

## 16. Frontend and application API boundary

The accepted React/Vite/TanStack frontend foundation is a projection/application
surface. Its Screenplay and Storyboard routes are hidden foundations, and its
Canvas/Agent/creative shells explicitly fail closed on missing backend contracts.
S0 adds no frontend implementation and does not upgrade surface maturity.

Future Studio queries/commands must be typed, tenant/Workspace/Project scoped,
paginated where collections can grow, and bound to exact versions/digests.
Effective access remains server-authoritative and unknown input disables action.
The frontend may hold selections, viewport, zoom, panel state, drafts prior to
submission, and command previews; it may not mint canonical IDs as accepted
truth, choose mutable latest silently, reconstruct provider graphs, store signed
URLs durably, or treat local undo/redo as canonical version history.

All absent APIs remain gaps in the companion ledger. H6/H7 interfaces not
present at the exact base are classified `DEPENDENCY_PENDING_CANONICALIZATION`;
Studio may not create local candidates, adapters to private implementation, or
temporary bypasses.

## 17. Current repository compatibility disposition

The base provides canonical owner material that constrains this contract:

- Operation definitions/targets/plans exist but are Timeline-specific and are
  insufficient for Studio extension.
- Timeline owns typed clips, immutable revisions, exact media/artifact pins,
  deterministic serialization, semantic diff, and persistence application.
- Media owns `MediaAssetId` and source truth; Artifact owns `ArtifactId`,
  `ContentDigest` validation, immutable materialization, query, and provenance.
- Render owns provider-neutral `RenderPlan`/`RenderGraph`; provider/worker
  identities are excluded from its plan fingerprint.
- Worker Fabric owns provider-native lowering/runtime placement; Provider Plugin
  Runtime contributes exact provider bindings; Workflow owns process.
- Identity has Project records, but Project identity is a string and the
  Workspace-to-Project projection remains insufficient for frontend scoping.
- current effective-access services are not the accepted five-factor composed
  projection required by the frontend gap ledger.
- Composite Resource contracts do not exist in this base and remain CR-0 work.
- no Studio backend module or canonical Screenplay/Scene/Shot/ShotPlan/
  Storyboard/ShotScene/previs application API exists.

The external repository audit gives exact path citations and classifications.
No absence above authorizes an invented API.

## 18. Stage gates and forbidden scope

S1 may begin only after Hermes freezes this S0 candidate and closes or accepts
the blocking dependency decisions. S1 is domain/application foundation only;
S2/S3 provider, preview, frontend, and integration scope require separate
authorization. At every stage:

- `UNCLASSIFIED` dependencies must equal zero;
- exact historical pins and deterministic digests are mandatory;
- direct Creative-to-Timeline mutation is forbidden;
- provider-native canonical semantics are forbidden;
- Artifact, Render, Commercial, Workflow, Media, Operation, Runtime, and
  Composite Resource authority inversions are forbidden;
- Studio-to-BMF-internal dependency is forbidden;
- duplicate Studio or Director Preview authority is forbidden;
- clean-forward schema and Modulith rules remain mandatory.

This candidate's readiness token is not a freeze claim:

`STUDIO_V3_S0_SINGLE_WRITER_READY_FOR_HERMES_FREEZE_REVIEW`
