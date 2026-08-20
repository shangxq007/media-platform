# Roadmap #20 Authoritative Effect Binding and Logical WHAT Closure — R5

Append-forward governance record — fifth bounded correction
(ROADMAP20_AUTHORITATIVE_EFFECT_BINDING_AND_LOGICAL_WHAT_CLOSURE_R5) closing
the two material blockers raised by ChatGPT independent review of R4.

## Chain

- R0 implementation `887f0c06` / publication `9538e73e`
- R1 implementation `8e0a11f4` / publication `d6660873`
- R2 implementation `4b6d6843` / publication `760b7e5c`
- R3 implementation `846121af` / publication `8f41caf4`
- R4 implementation `d1ea6c48` / publication `7a2b6a3a`
- **R5 implementation `97fa2aa01a212594f911679d91401e668968fc4a`
  (tree `8392f07cbd4efcc7bf2c330d23dfa3d628650af2`)**
- R5 implementation parent: `7a2b6a3a9430fd7cac5f866125f4c815ad8d8740`
- R5 publication: (this record, child of R5 implementation)

Append-forward only. R4 preserved untouched as historical evidence. No amend /
rebase / squash / reset / force-push. R4 claims that were found too strong by
independent review are explicitly corrected here (see Blocker A/B disposition) —
the R4 record itself is NOT rewritten.

## Independent verdict (R5 trigger)

- `ROADMAP_20_R4_INDEPENDENT_REVIEW = CORRECTION_REQUIRED`
- `ARCHITECTURE_PREMISE_FAILURE = NO`
- `ARCHITECTURE_ESCALATION_REQUIRED = NO`
- `MATERIAL_BLOCKERS = 2`
- R4 matrix: A1 authoritative binding = FAIL (BLOCKER); A2 pin retention = PASS;
  A3 fingerprint participation = PASS; A4 provenance = PASS;
  A5 logical WHAT completeness = FAIL (BLOCKER); B parameter framing = PASS;
  C single authority = PARTIAL_PASS; M sealed fail-closed = PASS.
- `RENDERPLAN_FORMAT_VERSION_COMPATIBILITY_REVIEW = KEEP_V1` (accepted).

## Blocker A — R4-A1 FAIL: binding was caller-relabelable

Root cause: `EffectSemanticBinding.of(String revisionId, List<EffectInstance>,
List<EffectDefinition>)` let the planning caller freely label arbitrary effect
state with any revision id. R4's checks (string equality + self-consistent
digest) could only prove "caller's label equals the timeline revision label" —
not that the Effect semantics actually belong to that authored revision.

### Repository reality (R5-A2 inspection)

- Authoritative effect semantics = wire `TimelineClipEffect(id, effectKey,
  parameters)` owned by `TimelineCandidate.Clip.effects` (revision-owned
  aggregate parsed from the revision's wire JSON via
  `InternalTimelineCandidateAdapter`).
- `EffectInstance`/`EffectDefinition` (semantic layer) have ZERO production
  construction points — they are the planning-boundary projection type.
- `TimelineRevision` carries revisionId + canonicalTimeline (clips) + contentDigest;
  it does NOT carry effects (TimelineDocument projection gap, E9).
- No revision-scoped effect repository / persisted ownership relation exists;
  the bounded domain authority contract below is the authoritative issuance
  path (allowed by instruction §6).

### Resolution (R5-A1/A3)

- `EffectSemanticBinding` is now a **final class with a PRIVATE constructor**
  and **NO public `of(...)` factory** — a planning caller cannot mint a binding
  from (revisionId, arbitrary effects).
- **`AuthoredEffectSemanticAuthority.issue(TimelineRevision, effects, defs)`**
  is the ONLY public issuance path. It:
  1. extracts the revision identity FROM the authoritative `TimelineRevision`
     object (never a caller string),
  2. performs a REAL ownership check: every effect's `applicationRange` must
     overlap at least one clip in the revision's canonical timeline — otherwise
     FAIL CLOSED (an effect that applies to nothing in the revision does not
     belong to it),
  3. delegates the content digest to the single Effect domain authority
     (`EffectSemanticStateCanonicalSemantics`),
  4. returns the immutable binding.
- Render consumes only authority-issued bindings; `VerifiedEffectSemanticSnapshotFactory`
  recomputes the digest via the same domain authority and fails closed on
  mismatch; `VerifiedRenderSemanticSnapshotFactory` fails closed on
  cross-revision combination.

### Relabel attack proof (R5-A4)

- `attackerCannotMintBindingThroughPublicApi`: no public constructor, no public
  `of()` factory, single 3-arg `issue` path — structural.
- `relabelAttackFailsClosedOwnershipCheck`: independent effect state R2 with
  application range [5,6) (outside R1's clip [0,2)) labeled as R1 →
  FAIL CLOSED at issuance (ownership check over the revision's actual clips).
- `relabelAttackWithOverlappingRangeStillBoundToRevisionObject`: even with an
  overlapping range, the binding's revision identity comes FROM the object —
  the caller cannot choose an arbitrary revision id (no string parameter).
- `crossRevisionCombinationFailsClosed`: R1 binding + R2 timeline → fail closed.
- `stateTamperFailsDigest`: altered effect state → digest mismatch → fail closed.
- `semanticEqualReconstructionDeterministic`: fresh reconstruction → same digest.

## Blocker B — R4-A5 FAIL: identity closure ≠ materialization closure

Root cause: the final RenderPlan carried only the Effect semantic reference
(digest/contract/revision) — "which effect state" — but the effect node's
materialization requirement held only category + static parameters. A future
physical planner could not recover application range, automation bindings,
definition identity/version, or temporal behavior without re-reading authored
Effect state.

### Resolution (R5-B1..B8)

`EffectMaterializationRequirement` extended with the COMPLETE typed,
provider-neutral Logical Effect WHAT (resolved once at materialization from the
verified authored snapshot; never re-read downstream):
- `effectInstanceId` — authoritative effect instance identity,
- `effectDefinitionId` + `effectDefinitionVersion` — definition identity AND
  version (def-blur@1 vs def-blur@2 distinguishable even with identical
  category/parameters),
- `category` — authority-resolved from the definition,
- `enabled` — disabled semantics = **OPTION A**: disabled effects do not
  materialize as execution nodes; the verified authored snapshot + plan
  reference retain them (explicit, tested),
- `applicationRange` — typed exact half-open rational range ([0,1) vs [0,2)
  distinguishable; no double, no String time hacks),
- `parameters` — typed key/value pairs (R4 shared pair encoder),
- `automationBindings` — typed key→reference pairs (authoritative automation
  reference recoverable from the Logical Plan, not hash-only),
- `temporalBehavior` — typed `EffectTemporalBehavior` (PRESERVE_DURATION etc.),
- derived capability requirements remain on the node (definition version and
  capabilities resolved from the SAME definition — self-consistent).

### No-authored-reread acceptance (R5-B8)

`noAuthoredRereadConsumerTest`: `PlanOnlyEffectConsumer` receives ONLY the
`RenderPlan` and extracts every required active Effect WHAT field — instance id,
definition id/version, category, enabled, exact application start/end, static
parameters, temporal behavior, capabilities, effect semantic reference —
without touching `EffectInstance` / `EffectDefinition` / authored repository
state. (The consumer type is defined in the test; it receives no second
argument beyond the plan.)

`PHYSICAL_PLANNER_CAN_CONSUME_LOGICAL_EFFECT_WHAT_WITHOUT_AUTHORED_REREAD = YES`

## R5-E — Digest contract review

`ContentDigest.sha256(String hex)` is a TYPE WRAPPER (constructs a ContentDigest
from a hex string; it does NOT re-hash). The effect digest pipeline is a single
SHA-256 over canonical bytes → hex → wrapper. No double hash. Documented and
tested (`digestContractIsSingleSha256`). KEEP_V1 compatibility re-audited at
freeze (see Format version).

## R5-F — Canonical collection ordering audit

Audited `EffectSemanticStateCanonicalSemantics`: `supportedMediaTypes`,
`deterministicProperties`, `requiredCapabilities`, and `parameterSchema`
`enumValues` are semantically UNORDERED set-like collections but were encoded
in insertion order → semantic-equal definitions with different insertion order
produced different canonical bytes (real defect). Fixed: deep-sorted in the
domain authority. Tested (`collectionOrderingIsInsensitive`).

## R4 passes preserved (R5-D)

- A2: final RenderPlan retains `EffectSemanticReference` — PASS (unchanged).
- A3: effect semantic contract version + digest participate in the plan
  fingerprint — PASS (unchanged).
- A4: provenance explains timeline revision + effect semantic reference;
  provenance is not fingerprint authority — PASS (unchanged).
- B: single shared parameter pair encoder in node identity + plan canonical —
  PASS (unchanged).
- M: sealed fail-closed (ColorDescription / ColorPrimaries /
  RenderMaterializationRequirement) — PASS (unchanged).

## Guard (R5 structural)

`verifyC20RenderPlanBoundaryGuard` extended: EffectSemanticBinding constructor
private (no mint path); AuthoredEffectSemanticAuthority present (single
issuance); materializer builds complete Logical WHAT (ofComplete + instance id +
application range); codec encodes instance id / definition version / application
range / temporal behavior; EffectSemanticStateCanonicalSemantics deep-sorts
unordered collections. All R2/R3/R4 guards retained.

## Format version

`RENDERPLAN_FORMAT_VERSION_COMPATIBILITY_REVIEW = KEEP_V1` — re-audited at R5
candidate freeze: 0 package-external `planFingerprintCanonical` /
`RenderPlanCanonicalCodec` consumers; `render_plan_json` belongs to
ProjectImportMetadata (unrelated); 0 cache-key dependencies; 0 API/GraphQL
consumers; 0 serialized-artifact / fixture-contract consumers. Roadmap #20
remains an unreleased correction chain (not merged to main).

## Test / gate results (final frozen SHA 97fa2aa0)

- R5 targeted (R5AcceptanceTest 14): relabel attack structural + ownership
  fail-closed + revision-object binding + cross-revision + tamper + semantic
  determinism; application range / definition version / automation / enabled /
  temporal recoverability; disabled OPTION A; no-authored-reread consumer;
  collection ordering; digest contract — **14 / 0 / 0**
- R4 regression (R4AcceptanceTest 16): all PASS — parameter delimiter
  collisions, hostile values, shared encoder, pin retention, fingerprint
  participation, provenance, sealed fail-closed — **16 / 0 / 0**
- renderplan package: **110 / 0 / 0**
- render-module: **2867 / 0 / 0 / skip 19**
- timeline-module: **771 / 0 / 0**; color-image-module: **20 / 0 / 0**;
  audio-module: **22 / 0 / 0**; font-text-module: **11 / 0 / 0**;
  extension-module: **314 / 0 / 0**; platform-app: **562 / 0 / 0 / skip 20**
- **FULL SUITE (recursive, --rerun-tasks, 176 tasks): 7558 / 0 / 0 / skip 43**
- bootJar: PASS; pfirr1RemediationCheck: PASS (+ all pfirr verify tasks);
  verifyC20RenderPlanBoundaryGuard: PASS (R2+R3+R4+R5 guards, 55 files);
  Modulith: PASS; git diff --check: PASS
- Architecture drift: 222 PASS; CIP2G6 + CIP2DG12 =
  **PRE_EXISTING_BASELINE_FAIL** — signature re-verified at R5 (both triggered
  by `RenderOutputRequirement` importing `platform.colorimage`
  ColorDescription/RasterSampleDescription — the frozen C14/C8 contract; file
  NOT modified by R5; no additional violation paths; R5 new types live in the
  timeline domain with no render/color references). CORRECTION_REGRESSION = NO.

## Scope audit

- Production: AuthoredEffectSemanticAuthority (+), EffectSemanticBinding
  (private ctor, mint path removed), EffectSemanticStateCanonicalSemantics
  (R5-F deep-sort), EffectMaterializationRequirement (complete Logical WHAT),
  DefaultRenderMaterializer (ofComplete + definition resolution),
  RenderPlanCanonicalCodec (R5-B fields), VerifiedEffectSemanticSnapshotFactory
  (authority digest), build.gradle.kts (R5 guards).
- Tests: R5AcceptanceTest (+), TestPlans + R3/R4 acceptance (adaptation).
- Scope drift: NONE. No provider/worker/device semantics, no physical planner,
  no TimelineDocument restructuring, no persistence migration, no Roadmap
  #21/#22 work.

## Blockers / escalation

- MATERIAL_BLOCKERS = 0
- ARCHITECTURE_ESCALATION = NONE
- NEW_REGRESSIONS = 0
- NEW_ARCHITECTURE_VIOLATIONS = 0
- FORMAT_VERSION_REVIEW_REQUIRED = NO (KEEP_V1 with audited evidence)

## R4 claims correction (append-forward, per instruction)

The R4 publication claimed "verified Timeline R1 + unrelated but internally
valid Effect state R2 cannot pass the boundary" and "physical planner can
consume all Effect WHAT without authored reread". Independent review found these
claims too strong: R4-A1 binding was caller-relabelable and R4-A5 Logical WHAT
was incomplete. These claims are corrected by the R5 code (authority-issued
binding with ownership check; complete typed Logical Effect WHAT) and the
corrected invariants are now verified by the tests above. The R4 historical
record is preserved unchanged.

## Final recommendation

**READY_FOR_CHATGPT_R5_INDEPENDENT_REVIEW**

Roadmap #20 is NOT closed. Merge to main NOT authorized. Roadmap #21/#22 NOT
started. Closure remains ChatGPT's decision.
