# ROADMAP20_EFFECT_SEMANTIC_SNAPSHOT_REVISION_PIN_FINAL_IMPLEMENTATION

Status: PUBLISHED (pending ChatGPT independent final review)
Base: bf7a2702480d9ad73ead986e05602e974b6f022a
Branch: agent/roadmap20-renderplan
Task type: FINAL BOUNDED PRODUCTION IMPLEMENTATION + FCV + APPEND-FORWARD PUBLICATION

## 1. Architecture state (frozen, not reopened)

Option B implementation: TimelineRevision → exact immutable pin
(EffectSemanticSnapshotReference: snapshotId + contentDigest + contractVersion) →
immutable EffectSemanticSnapshot (target-bound ordered Effect semantics + exact
EffectDefinition semantic snapshot + contract version + deterministic content
digest) → VerifiedEffectSemanticSnapshot → VerifiedRenderSemanticSnapshot →
Logical RenderPlan/RenderGraph.

Identity separation (binding-vs-semantic-identity correction, bf7a2702):
- SnapshotId = binding/object identity (handle)
- ContentDigest = semantic content commitment
- RevisionId = revision DAG identity
- SnapshotId MUST NOT enter any canonical semantic digest.

## 2. Snapshot model (timeline-module semantics/effect)

- EffectSemanticContractVersion — typed value "effect-semantics-v1"; persisted,
  serialized, verified, included in the semantic commitment; unknown version
  FAILS CLOSED.
- EffectSemanticSnapshotId — immutable authority handle (generate() during
  domain mint only).
- EffectSemanticSnapshotReference — exact pin (snapshotId + contentDigest +
  contractVersion); semanticCommitmentCanonical() excludes the id.
- EffectDefinitionSnapshot — D1 exact embedded definition semantics
  (definitionId, version, category, supportedMediaTypes, parameterSchema,
  temporalBehavior, deterministicProperties, requiredCapabilities +
  definitionContentDigest); supportedBackendCapabilities is EXECUTION/PROVIDER
  metadata and is NOT part of the semantic digest (provider-neutrality).
- EffectSemanticEntry — typed target-bound entry; automationBindings EMPTY (V1;
  non-empty unverified automation FAILS CLOSED — SA5).
- EffectSemanticSnapshot — immutable; package-private constructor; minting is
  DOMAIN AUTHORITY ONLY.
- EffectSemanticSnapshotCanonicalSemantics — single canonical codec; snapshot id
  EXCLUDED from digest; per-target authored order preserved; cross-target
  deterministic ordering.
- EffectDefinitionCanonicalSemantics — deterministic definition digest.
- EffectSemanticSnapshotAuthority — sole minting path: target clip existence,
  definition resolution + version equality, mediaType DERIVED (track kind ∩
  supportedMediaTypes; incompatible FAIL CLOSED), duplicate instance id FAIL
  CLOSED, automation fail-closed.
- EffectDefinitionVersionRegistry — interface; InMemory (domain tests) + JDBC
  (durable) implementations; enforces (definitionId, version) → exact digest
  across ALL snapshots incl. restart.
- EffectSemanticSnapshotStore — interface; InMemory + JdbcEffectSemanticSnapshotStore
  (dedicated timeline_effect_snapshot row, V2 migration; BI4 immutability;
  idempotent same-content re-store; exact historical reload).
- TimelineRevisionEffectSemanticCommitment — revision semantic digest =
  H(TimelineCanonicalSemanticDigest, EffectSemanticContractVersion,
  EffectSemanticSnapshotContentDigest); excludes snapshot id, revision id,
  provider/backend metadata, provenance.

## 3. Revision pin

Every canonical revision semantics commit includes the Effect term; the pin is
exact (id + digest + version). R1:S1→R1:S2 (even semantic-equal) is FORBIDDEN
(HISTORICAL_EFFECT_SNAPSHOT_BINDING_IS_IMMUTABLE_V1) — verifier FAILS CLOSED.

## 4. Render boundary

- VerifiedEffectSemanticSnapshotFactory — sole verified path:
  verified(EffectSemanticSnapshot snapshot, EffectSemanticSnapshotReference
  expectedReference, String revisionId); verifies id equality (BI2/RP3-C),
  digest recomputation (BI3/RP2), contract version (BI5); caller effects/
  definitions/projection/binding parameters RETIRED.
- VerifiedRenderSemanticSnapshotFactory — verified(TimelineRevision, digester,
  snapshot, reference); no arbitrary Effect lists.
- DefaultRenderMaterializer — consumes derived per-clip effect views
  (effectsForClip(clip)); applicationRange DERIVED from clip extent
  (APPLICATION_RANGE_AUTHORITY_V1); mediaType DERIVED (EFFECT_MEDIA_TYPE_IS_DERIVED_V1);
  no mutable/latest EffectDefinition lookup.
- CapabilityRequirement identity = CapabilityId + ContractVersionRange — single
  encoder shared by final plan serialization and local Effect node identity
  (§33 closure).

## 5. Legacy hydration (deterministic-or-fail-closed)

- effectInstanceId = wire id; definitionId = wire effectKey; target = containing
  track/clip; definitionVersion MUST NOT be invented — unresolvable FAILS
  CLOSED (L5/D6); enabled = TRUE (LEGACY_EFFECT_ENABLED_DEFAULT_V1);
  applicationRange = DERIVED clip extent; mediaType = DERIVED; automationBindings
  = EMPTY.

## 6. EMPTY vs MISSING

- NEW canonical revision with no Effects = authoritative EMPTY
  EffectSemanticSnapshot (generated id, effect-semantics-v1, deterministic empty
  digest) — pinned exactly.
- LEGACY revision with NULL pin = MISSING authority — distinct from EMPTY;
  legacy policy; no latest lookup, no caller completion (RP5).
- EMPTY1..EMPTY5 implementation tests cover digest determinism, pin presence,
  restart reload, legacy distinction, plan-with-no-effect-nodes.

## 7. Persistence

- Storage direction B (dedicated immutable snapshot rows), repository-reality
  adapted: this repository's Flyway governance is V1-only (GCR-2 consolidation —
  flyway_schema_history must contain exactly one canonical V1). NO new migration
  was introduced. Effect snapshot rows live in the EXISTING immutable
  `timeline_snapshot` table with the `esnap_` id prefix; the effect contract
  version is stored in `schema_version`; canonical JSON in `payload_json`.
- JdbcEffectSemanticSnapshotStore — durable store (production wiring
  candidate); InMemory is domain-test only. BI4 immutability (same id different
  content FAILS CLOSED), idempotent same-content re-store, exact historical
  reload, missing row = LEGACY MISSING (not EMPTY).
- JdbcEffectDefinitionVersionRegistry — durable (definitionId, version) → digest
  collision enforcement across restarts (D1/§38), scanning `timeline_snapshot`
  effect rows.
- EffectSemanticSnapshotJsonCodec — deterministic payload serialization;
  deserialize recomputes and verifies digest (BI3/RP2 across restart).
- Legacy NULL pin semantics preserved: timeline_revision carries no Effect pin
  columns — legacy revisions remain legacy (MISSING authority), never EMPTY.

## 8. Canonical revision write paths (inventory)

See /tmp/.../canonical-revision-write-paths.txt. Summary:
- CANONICAL_NEW_REVISION_WRITE_PATHS = 4 (saveRevision, restoreRevision,
  command-apply, merge-apply)
- NEW REVISION PATHS WITH EFFECT SNAPSHOT/PIN/COMMITMENT = 0 — zero production
  Effect data flow (E9 gap: TimelineDocument has no effects field; wire effects
  are 3-field legacy projection). NO path fabricates Effect authority; every
  path without Effect input persists NULL pin = LEGACY MISSING.
- CANONICAL BYPASS PATHS = 0
- LEGACY READ-ONLY PATHS = snapshot findPayload readers (diff/merge/semantic-diff)
- RETIRED OLD WRITE PATHS = 0 (caller-assembly verified factories removed)
- When Effect authoring exists, every new canonical revision MUST mint+persist
  snapshot, pin exact reference, include the Effect term in the revision
  semantic commitment.

## 9. Canonical acceptance matrix (37/37)

See /tmp/.../canonical-acceptance-matrix.txt. RP1..RP5, SA1..SA5, D1..D6,
SO1..SO4, L1..L5, R1..R5, BI1..BI5 — all mapped to exact class.method, all PASS.

## 10. FCV results

- compileJava / compileTestJava: PASS
- renderplan package: 140/0/0 (incl. R6AcceptanceTest with RP4/RP5/SO4/EMPTY1/EMPTY5)
- timeline-module: 787/0/0 (incl. EffectSemanticSnapshotFinalAcceptanceTest: RP3-A/B, SA1/SA2, D2-D5, L1-L5, SO2, BI4/BI5, EMPTY2/EMPTY4)
- render-module: 2902/0/0/19
- platform-app: 567/0/0/20 (incl. EffectSnapshotPersistenceIntegrationTest 5/5: durable restart, BI4 durable, D1 durable collision, codec round-trip, EMPTY3)
- FULL SUITE --rerun-tasks: 7614 / 0 failures / 0 errors / 43 skipped (18m48s; baseline R5 = 7558 — +56 new tests)
- C20 structural guard: PASS (55 files)
- pfirr1RemediationCheck: PASS
- platform-app bootJar: PASS
- verifyC1Cnm1RedGates (drift + fail-closed architecture gates): PASS
- Modulith gates (ModularityTest / ModulithDocumentationGenerationTest): PASS (in full suite)
- git diff --check: PASS
- V1-only Flyway governance: PRESERVED (12 SchemaEquivalence/Isolation tests re-verified PASS)

## 11. History

bf7a2702 → FINAL_IMPLEMENTATION (parent bf7a2702) → FINAL_PUBLICATION (parent
FINAL_IMPLEMENTATION). Append-forward only. No amend/reset/rebase/squash/force.

## 12. Governance

- Implementation commit: (see report)
- Publication commit: (see report)
- Evidence: /tmp/ROADMAP20_EFFECT_SEMANTIC_SNAPSHOT_REVISION_PIN_FINAL_IMPLEMENTATION/
- Blockers: none material
- Architecture escalation: NONE
- main changed: NO (07de0092 unchanged)
- Merge: NO
- Roadmap #20 closed: NO
- #21/#22 started: NO

## 13. Deferred non-goals (unchanged)

Effect automation subsystem, full Effect catalog, Physical Planner, Operation
Algebra, semantic rewrite, cost optimizer, polyglot algorithm providers, user
compute.
