# GCR-2 Correction V1 — Artifact Query Tenant Isolation + Publication Discipline

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Correction: GCR2_CORRECTION_V1_ARTIFACT_QUERY_TENANT_ISOLATION_AND_PUBLICATION_DISCIPLINE

## Governance Context

- Original GCR-2 tip: `952f102cc5754371253b77ecfaa5d19b53d1976c`
  (tree `d4511e7ad8a0a3ee095de194208fcb5805381486`)
- Independent ChatGPT GCR-2 final review result: FAIL_BOUNDED_CORRECTION_REQUIRED
- ORIGINAL_GCR2_PUBLICATION_DISCIPLINE = NONCONFORMING_BUT_PRESERVED_AS_HISTORY
  (the original GCR-2 publication doc was committed inside candidate 952f102c and
  claimed FINAL_FCV = PASS before FCV ran. It remains valid historical evidence —
  no history rewrite was performed.)
- CORRECTED_GOVERNANCE_CHAIN = APPEND_FORWARD:
  `952f102c → ec24b01f (correction candidate) → FINAL FCV PASS → this publication
  → publication record`
- CORRECTION_PUBLICATION = AUTHORITATIVE_CLOSURE_SUPPLEMENT
- GCR2_CORRECTION_PUBLICATION_MUST_POSTDATE_FROZEN_CORRECTION_CANDIDATE_V1: this
  document postdates the frozen correction candidate `ec24b01f`.

## Blocker 1 — Production ArtifactQuery tenant conformance

Independent review: production `JooqArtifactQueryService` did not fully honor
`ArtifactQueryService` tenant-isolation semantics; `tenantId` was decorative in
several DB queries.

### Defects fixed

1. `ArtifactRepository.listReplicas(tenantId, artifactId)` — replica metadata
   (storage_object_id, provider_id, replica_id, region) was queryable cross-tenant
   (filtered only on artifact_id). FIXED: scoped via canonical Artifact ownership
   using an EXISTS subquery on `artifact.tenant_id`.
2. `ArtifactRepository.findReplica(...)` — same defect. FIXED identically.
   `findReplica` was also added to the `ArtifactQueryService` contract (bounded
   interface addition per correction directive §4).
3. `ArtifactRelationRepository.findByArtifactId(artifactId)` — global query, no
   tenant scoping. `listParents`/`listChildren`/`getDirectProvenance` consumed it
   unscoped → cross-tenant parent/child ArtifactIds and provenance relationships
   observable. FIXED: new `findByArtifactIdScopedToTenant(tenantId, artifactId)`
   joins artifact on BOTH source and target peers; both must have
   `tenant_id = requested tenant` (zero schema change — no tenant column added to
   artifact_relation).
4. `JooqArtifactQueryService.boundedAncestorTraversal / boundedDescendantTraversal`
   — built on unscoped relations; could cross tenant at any hop. FIXED: root
   existence check in the requested tenant + every hop traversed through
   tenant-scoped peer lookups (ARTIFACT_PROVENANCE_TRAVERSAL_NEVER_CROSSES_TENANT_BOUNDARY_V1).
5. maxDepth divergence: Jooq threw IllegalArgumentException for maxDepth < 1 while
   InMemory returned empty. FIXED: both return EMPTY (contract frozen by
   ArtifactQueryServiceTest.traversalWithZeroDepthReturnsEmpty).
6. findByContentDigest limit divergence: InMemory clamps `Math.max(1, limit)`;
   jOOQ passed raw limit. FIXED: both behave as limit = 1 for limit <= 0.
7. `InMemoryArtifactQueryService.listParents/listChildren/getDirectProvenance`
   checked the ROOT tenant but not the PEER tenant — malformed cross-tenant
   relations (A2@tenant-a → B1@tenant-b) would surface B1. FIXED: peers must
   belong to the same tenant (conformance with jOOQ adapter).

### Frozen semantics

- ARTIFACT_QUERY_IMPLEMENTATIONS_HAVE_IDENTICAL_TENANT_ISOLATION_SEMANTICS_V1
- ARTIFACT_QUERY_TENANT_ARGUMENT_IS_SEMANTIC_NOT_DECORATIVE_V1
- CROSS_TENANT_ARTIFACT_METADATA_DISCLOSURE_FAILS_CLOSED_V1
- ARTIFACT_PROVENANCE_TRAVERSAL_NEVER_CROSSES_TENANT_BOUNDARY_V1

## PostgreSQL Tenant-Isolation Tests

`JooqArtifactQueryServiceTenantIsolationTest` (12 tests, real PostgreSQL):

- Q1 getArtifact cross-tenant → EMPTY
- Q2 listReplicas cross-tenant → EMPTY
- Q3 findReplica cross-tenant → EMPTY
- Q4 listParents cross-tenant → EMPTY
- Q5 listChildren cross-tenant → EMPTY
- Q6 getDirectProvenance cross-tenant → EMPTY
- Q7 boundedAncestorTraversal cross-tenant → EMPTY
- Q8 boundedDescendantTraversal cross-tenant → EMPTY
- Q9 findByContentDigest cross-tenant → no tenant-a results for tenant-b
- Q10 same-tenant positive behavior (artifact/replica/parents/children/provenance/traversal)
- maxDepth < 1 → EMPTY
- Malformed cross-tenant relation (A2@tenant-a → B1@tenant-b): B1 never surfaces
  in tenant-a traversal / listChildren / provenance; A2 never surfaces in
  tenant-b traversal.

## InMemory / Jooq Conformance

INMEMORY_JOOQ_CONFORMANCE = PASS (Q-matrix + malformed relation + maxDepth +
limit; ARTIFACT_QUERY_IMPLEMENTATION_CONFORMANCE_FAILURE_COUNT = 0)

## GCR-1 / GCR-2 Regression

- GCR1_GUARD = PASS (verifyGcr1CorrectionV2IngressAuthority)
- GCR2_GUARD = PASS (verifyGcr2ArtifactAuthority)
- GCR2_CORRECTION_V1_GUARD = PASS (verifyGcr2CorrectionV1: replica/relation/
  provenance/traversal tenant-scoped at DB level; InMemory/Jooq conformance;
  maxDepth/limit normalized)
- JOOQ_FOUNDATION = PASS; MODULITH = PASS; ARCHITECTURE_DRIFT = PASS (224)
- Timeline pin validation (valid/missing/digest/cross-tenant) = PASS
- T7 hard atomicity (Gcr2PinRegistrationFailureRollbackTest) = PASS
- Artifact GC/lifecycle (ArtifactPinProtectionTest / ArtifactGcServiceTest /
  ArtifactLifecycleServiceTest) = PASS

## Whole Repository

- SUITES = 907, TESTS = 7156, FAILURES = 0, ERRORS = 0, SKIPPED = 43
- TEST_DELTA_FROM_GCR2_952F102C = +1 suite / +12 tests (new
  JooqArtifactQueryServiceTenantIsolationTest; no deletions)

## Gates

GCR1_GUARD / GCR2_GUARD / GCR2_CORRECTION_V1_GUARD / JOOQ_FOUNDATION /
MODULITH / ARCHITECTURE_DRIFT / MAP_DRIFT (41/23/3, failures 0) /
MAP_DETERMINISM (3x byte-identical: 4a5f7a9f4ca4a8cd997962f1728de31968dfef648d2360da2db95d203fa9f144) /
BOOTJAR / PFIRR1 / CREDENTIAL_SCAN (0 in staged set) / GREENFIELD_RESIDUE
(12/12 zero) — all PASS.

## Candidate

- CORRECTION_CANDIDATE_SHA = `ec24b01fc95764b44e2207ec3fd4dd520800f2f8`
- CORRECTION_CANDIDATE_TREE = `846972782afaf19fc450ac4a05324ef71e791e55`
- Ancestry: 952f102c → ec24b01f (single commit, linear, no merge/rebase/squash)
- Commit: fix(gcr2): enforce Artifact query tenant isolation
- The candidate contains NO publication claims (hard governance requirement).

## Final FCV

GCR2_CORRECTION_V1_FINAL_FCV = PASS (19/19) — run against the frozen candidate
`ec24b01f` before this publication was created.

## Deferred Observations (NOT in correction scope)

- ArtifactCatalogService may return an ArtifactCatalogEntry after canonical commit
  failure; Timeline validation uses canonical ArtifactQueryService, so
  projection-only entries cannot masquerade as canonical Artifact. Deferred.
- ArtifactLifecycleService retains URI-era reference-scanning behavior
  (ArtifactCatalogEntry.storageUri). Not the GCR-2 closure blocker. Deferred.
