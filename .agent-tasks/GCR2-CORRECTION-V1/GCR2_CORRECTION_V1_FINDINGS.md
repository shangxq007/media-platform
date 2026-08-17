# GCR2_CORRECTION_V1 — FINDINGS

Correction: GCR2_CORRECTION_V1_ARTIFACT_QUERY_TENANT_ISOLATION_AND_PUBLICATION_DISCIPLINE
Base: 952f102cc5754371253b77ecfaa5d19b53d1976c (tree d4511e7ad8a0a3ee095de194208fcb5805381486)

## BLOCKER 1 — Production ArtifactQuery tenant conformance

Independent ChatGPT review identified that the production jOOQ adapter does not
fully honor ArtifactQueryService tenant-isolation semantics. Confirmed by code
inspection:

### Defect inventory (production code)

1. `ArtifactRepository.listReplicas(tenantId, artifactId)` — SQL filters ONLY on
   artifact_id; tenantId is ignored (decorative). Cross-tenant replica metadata
   (storage_object_id, provider_id, replica_id, region) observable.
2. `ArtifactRepository.findReplica(tenantId, artifactId, replicaId)` — same:
   tenantId ignored.
3. `ArtifactRelationRepository.findByArtifactId(artifactId)` — global query, no
   tenant scoping at all. listParents/listChildren/getDirectProvenance in
   JooqArtifactQueryService consume it unscoped → cross-tenant parent/child
   ArtifactIds and provenance relationships observable.
4. `JooqArtifactQueryService.boundedAncestorTraversal / boundedDescendantTraversal`
   — built on unscoped listParents/listChildren → traversal can cross tenant
   boundary at any hop.
5. `JooqArtifactQueryService.maxDepth < 1` throws IllegalArgumentException;
   `InMemoryArtifactQueryService.maxDepth < 1` returns empty list. CONTRACT
   DIVERGENCE (frozen/public test `traversalWithZeroDepthReturnsEmpty` expects
   EMPTY).
6. `findByContentDigest` limit: InMemory clamps `Math.max(1, limit)`; jOOQ passes
   raw limit to SQL LIMIT. DIVERGENCE for limit <= 0.
7. `InMemoryArtifactQueryService.listParents/listChildren/getDirectProvenance`
   check the ROOT artifact tenant but not the PEER artifact tenant — a malformed
   cross-tenant relation (A2@tenant-a → B1@tenant-b) would surface B1 to
   tenant-a queries. (Traversal methods DO check every hop; the direct
   relation-query methods do not.)

### Frozen semantics (authoritative)

- ARTIFACT_QUERY_IMPLEMENTATIONS_HAVE_IDENTICAL_TENANT_ISOLATION_SEMANTICS_V1
- ARTIFACT_QUERY_TENANT_ARGUMENT_IS_SEMANTIC_NOT_DECORATIVE_V1
- CROSS_TENANT_ARTIFACT_METADATA_DISCLOSURE_FAILS_CLOSED_V1
- ARTIFACT_PROVENANCE_TRAVERSAL_NEVER_CROSSES_TENANT_BOUNDARY_V1
- maxDepth < 1 → EMPTY (frozen by ArtifactQueryServiceTest.traversalWithZeroDepthReturnsEmpty)
- limit <= 0 → behaves as limit = 1 (InMemory `Math.max(1, limit)`, normalized)

### Fix approach

- Database-level tenant scoping preferred (not post-query Java filtering):
  - listReplicas/findReplica: EXISTS subquery on artifact.tenant_id
  - relation queries: JOIN artifact on both source and target; both must belong
    to the requested tenant (no tenant column added to artifact_relation —
    zero schema change).
  - traversal: tenant-scoped at every hop (root existence check + per-hop peer
    tenant check).
- maxDepth/limit normalized to InMemory behavior (contract frozen by tests).
- InMemory direct relation methods gain peer-tenant checks for malformed
  cross-tenant relation defense (conformance with jOOQ).

## BLOCKER 2 — Publication governance discipline

GCR-2 candidate 952f102c contains both implementation and
docs/architecture/governance/gcr-2-artifact-authority.md which claims
FINAL_FCV = PASS — i.e. the publication document was committed inside the
implementation candidate BEFORE Final FCV ran. Independent review rejected
closure partly on this governance-order violation.

Policy (frozen):
- History is immutable: 952f102c is NOT rewritten, rebased, or amended.
- ORIGINAL_GCR2_PUBLICATION_DISCIPLINE = NONCONFORMING_BUT_PRESERVED_AS_HISTORY
- Corrected chain (append-forward):
  952f102c → CORRECTION CANDIDATE → FINAL FCV PASS → CORRECTION PUBLICATION
  → PUBLICATION RECORD
- GCR2_CORRECTION_PUBLICATION_MUST_POSTDATE_FROZEN_CORRECTION_CANDIDATE_V1
- Correction candidate contains NO publication claims (hard requirement).

## Scope decisions

NO_GCR2_ARCHITECTURE_REDESIGN_REQUIRED = YES
NO_SCHEMA_REDESIGN_REQUIRED = YES (zero V1 change; tenant scoping via JOIN/EXISTS)
NO_GCR5_GCR6_SCOPE = YES

## Deferred observations (recorded, NOT in scope)

- ArtifactCatalogService may return an ArtifactCatalogEntry after canonical
  commit failure. Timeline validation uses canonical ArtifactQueryService, so
  projection-only entries cannot masquerade as canonical Artifact. Not a current
  authority violation; deferred.
- ArtifactLifecycleService retains URI-era reference-scanning behavior based on
  ArtifactCatalogEntry.storageUri. Not the GCR-2 closure blocker; deferred.
