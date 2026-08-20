# Roadmap #20 Effect Snapshot — Binding vs Semantic Identity Correction

Append-forward governance correction
(ROADMAP20_EFFECT_SNAPSHOT_BINDING_VS_SEMANTIC_IDENTITY_CORRECTION) resolving
the single material blocker raised by ChatGPT independent review of the
Option B contract closure (a2bbf5c8): the semantic/object-identity
contradiction between snapshot-id participation in revision semantic identity
and the correctly-declared id≠digest separation.

This is a GOVERNANCE-ONLY CONTRACT CORRECTION.
`PRODUCTION_IMPLEMENTATION_CHANGES = 0`.

## 1. Trigger

- `ROADMAP20_EFFECT_AUTHORITY_OPTION_B_CONTRACT_CLOSURE_REVIEW =
  CORRECTION_REQUIRED`
- `OPTION_B_SELECTION = PASS`; `ARCHITECTURE_ESCALATION_RESOLUTION = B — ACCEPTED`;
  `REOPEN_A_B_C = NO`
- `MATERIAL_BLOCKERS = 1`: EffectSemanticSnapshotId was incorrectly included in
  canonical Timeline revision semantic identity while the same governance
  contract also correctly declared `EffectSemanticSnapshotId ≠
  EffectSemanticSnapshotContentDigest` and that two snapshots with identical
  semantic content may have different ids but identical digests.

## 2. Previous Option B closure retained

All accepted Option B contracts remain FROZEN and MUST NOT be reopened
(complete list in §4 of roadmap-20-effect-authority-option-b-contract-closure.md):
snapshot pinned by revision; revision pins immutable semantics; snapshot is
primary typed authority / wire is derived projection; minting is domain
authority only; legacy hydration deterministic-or-fail-closed; definition
semantics exactly pinned (D1); definition version content immutable; legacy
enabled TRUE / applicationRange derived / mediaType derived; unverified
automation fail-closed; stack order authored ordered; Render consumes verified
snapshot only; one canonical revision persistence path; effect-semantics-v1
schema evolution; supportedBackendCapabilities = execution/provider metadata;
no second Effect revision DAG.

## 3. Exact contradiction found

- a2bbf5c8 §3 (L46): `TimelineRevisionSemanticContent = TimelineCanonicalContent
  + EffectSemanticSnapshotReference (EffectSemanticSnapshotId, ContentDigest,
  contractVersion)` — reads as snapshot id participating in SEMANTIC identity.
- a2bbf5c8 §10 (L152): `EffectSemanticSnapshotId (stable handle) ≠
  EffectSemanticSnapshotContentDigest (semantic commitment)`; two snapshots with
  identical content may share digest and differ in id.

Contradiction: different-id/identical-content snapshots are semantically valid
(§10) yet would yield different Timeline revision semantic digests if id enters
the digest (§3) — violating "id is a handle, not meaning".

## 4. Binding identity vs semantic commitment

FROZEN: `EFFECT_SNAPSHOT_BINDING_IDENTITY_IS_DISTINCT_FROM_SEMANTIC_COMMITMENT_V1`

Three roles, all retained:
- EffectSemanticSnapshot content = semantic AUTHORITY
- ContentDigest = semantic commitment to that authority (participates in
  canonical semantic equality, Timeline revision semantic digest, Effect
  semantic digest, Render semantic equality, Operation semantic normalization,
  canonical cache equality)
- SnapshotId = exact immutable object/binding handle (locate + verify pinned
  snapshot; binding integrity; does NOT represent semantic content equality)

FROZEN: `EFFECT_SNAPSHOT_HANDLE_DOES_NOT_PARTICIPATE_IN_CANONICAL_SEMANTIC_DIGEST_V1`
— SnapshotId must NOT affect Effect semantic equality, Timeline semantic
equality, Timeline revision semantic content digest, Effect semantic content
digest, Render semantic equality, Operation semantic normalization, or
canonical semantic cache equality (unless a future contract explicitly defines
snapshot identity as authored semantic content — current Option B does NOT).

FROZEN: `SEMANTIC_EQUALITY_DOES_NOT_IMPLY_BINDING_IDENTITY_V1` and
`BINDING_IDENTITY_DOES_NOT_DEFINE_SEMANTIC_EQUALITY_V1`.

FROZEN: `REVISION_GRAPH_IDENTITY_IS_DISTINCT_FROM_SEMANTIC_CONTENT_IDENTITY_V1`
— TimelineRevision.revisionId (DAG node identity) ≠ revision semantic content
digest; different revision ids may carry equal semantic digest (branch history,
provenance, no-op edits, future dedup, merge analysis). Aligns with the
existing principle revision identity ≠ Timeline content hash.

## 5. Correct revision semantic digest

FROZEN: `TIMELINE_REVISION_EFFECT_SEMANTIC_COMMITMENT_V1`

OLD (FORBIDDEN): H(TimelineCanonicalContent, EffectSemanticSnapshotId,
ContentDigest, ContractVersion).

CORRECT:
```
TimelineRevisionSemanticDigest =
H(TimelineCanonicalSemanticDigest, EffectSemanticContractVersion,
  EffectSemanticSnapshotContentDigest)
```
Equivalent deterministic bounded encodings allowed.

Invariant: same Timeline semantic content + same Effect content digest + same
contract version ⇒ same revision semantic digest, regardless of snapshot id.

## 6. Historical binding immutability

FROZEN: `HISTORICAL_EFFECT_SNAPSHOT_BINDING_IS_IMMUTABLE_V1`

R1 pins (S1, D, V1). Caller supplies (S2, D, V1) — even semantically
identical: R1 historical binding MUST NOT silently become S2. R1→S1 cannot
mutate to R1→S2 without a new persisted revision/binding event. Semantic
equality does NOT authorize historical reference mutation.

## 7. Semantic equality vs binding equality

- SEMANTIC EQUALITY: contractVersion equal AND contentDigest equal (subject to
  canonical digest verification). S1.id=snap-a, S2.id=snap-b, same digest D,
  same V1 ⇒ EffectSemanticEqual(S1,S2) = YES.
- BINDING EQUALITY: exact pinned reference identity match. Reference(S1) ≠
  Reference(S2) because snap-a ≠ snap-b — valid and expected.

## 8. Verification contract

Verifier checks BOTH:
1. semantic integrity: recomputeCanonicalDigest(supplied.content) ==
   expected.contentDigest
2. binding integrity: supplied.id == expected.snapshotId; supplied.contentDigest
   == expected.contentDigest; supplied.contractVersion == expected.contractVersion

Any failure ⇒ FAIL CLOSED. Timeline revision SEMANTIC digest computation uses
timeline digest + contract version + content digest (NOT snapshotId).

## 9. Corrected RP3

- RP3-A (different semantic content): D1 ≠ D2 ⇒ revision semantic digest MUST
  differ.
- RP3-B (different handle, same semantics): A ≠ B, same D, same V1 ⇒ revision
  semantic digest MUST be EQUAL.
- RP3-C (historical binding immutability): R1→B where A ≠ B even if
  digest equal ⇒ FORBIDDEN; verifier with B for existing R1 ⇒ FAIL CLOSED
  unless a new valid revision with binding B was canonically created.

## 10. Additional acceptance attacks (BI1-BI5)

BI1 id-does-not-pollute-semantics; BI2 id-still-protects-binding; BI3
content-tamper; BI4 digest-changes-semantics; BI5 contract-version-participation
(detail: acceptance-attacks.txt). Plus corrected RP3-A/B/C. RP1/RP2/RP4/RP5
unchanged.

## 11. Definition identity consistency (NO change)

(definitionId, version) = domain/version identity; contentDigest = exact
semantic content commitment. Frozen rule UNCHANGED: same id+version, different
digest ⇒ FAIL CLOSED. NOT analogous to snapshot handles because (definitionId,
version) carries domain semantic version identity semantics, whereas snapshot id
is only an immutable object handle. Engineers must not generalize the new rule
to definition identity.

## 12. Render implications

Render still verifies EXACT binding (same content digest is NOT sufficient to
substitute a different snapshot object for a historical pin). RenderPlan
canonical semantic identity must NOT incorporate irrelevant storage handles.
Local node identity: NO snapshot id in every local Effect node id; NO global
digest in unrelated local node ids (R6 locality preserved). Capability encoder
follow-up unchanged (one encoder = CapabilityId + ContractVersionRange).

## 13. Cache / future algebra implications

OPERATION_ALGEBRAIC_CONTRACT_V1 / SEMANTIC_ANALYSIS_FOUNDATION_V1 /
SEMANTIC_REWRITE_SYSTEM_V1 / COST_OPTIMIZATION_ONLY_OVER_PROVEN_LEGAL_PLAN_SPACE_V1
adopted. Future semantic normalization / canonical equality / cache reuse /
incremental planning must not miss equivalence because storage ids differ.
Object/storage identity stays separate from canonical semantic identity.
`ROADMAP_20_SCOPE_CHANGE = NO` — no optimizer work now.

## 14. Implementation impact

No production change this round. Future implementation round
(ROADMAP20_EFFECT_SEMANTIC_SNAPSHOT_REVISION_PIN_FINAL_IMPLEMENTATION) will
execute frozen workstreams A-P with the corrected digest contract: snapshot
model/codec/digest (digest excludes id), reference (id+digest+version), revision
semantic commitment (digest excludes id), atomic persistence, legacy hydration,
caller API retirement, verified factory, Render boundary, ordered stacks,
automation empty/fail-closed, R6 preservation, capability encoder unification,
guards, 31+ acceptance attacks (RP 5 + SA 5 + D 6 + SO 4 + L 5 + R 5 + BI 5 +
RP3 3 = 38 total with corrections), targeted/module/full FCV, append-forward
publication.

## 15. Explicit non-goals (unchanged)

No plugin marketplace / catalog / generic asset / automation subsystem /
physical planner / FFmpeg/GPU / Operation Algebra / DSL / second revision DAG /
Artifact conflation. No schema/DB/API changes this round.

## 16. Governance STOP state

- Supersession scope: SUPERSEDES a2bbf5c8 = (a) semantic digest participation
  of EffectSemanticSnapshotId, (b) broad RP3 wording ONLY. DOES NOT supersede
  all other accepted Option B contracts.
- History immutable: a2bbf5c8 NOT rewritten.
- Merge: NOT MERGED (main 07de0092). Roadmap #20: NOT CLOSED. #21/#22: NOT
  STARTED.

## Evidence

`/tmp/ROADMAP20_EFFECT_SNAPSHOT_BINDING_VS_SEMANTIC_IDENTITY_CORRECTION/` —
contradiction, semantic-vs-binding-identity, revision-digest-contract,
rp3-correction, acceptance-attacks, definition-identity-consistency,
render-implications, future-cache-algebra-implications, repository-state,
commands.log, MANIFEST.sha256. Governance evidence only.

**READY_FOR_CHATGPT_BINDING_VS_SEMANTIC_IDENTITY_REVIEW**
