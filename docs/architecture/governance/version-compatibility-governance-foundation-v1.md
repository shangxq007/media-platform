---
type: architecture-governance-record
milestone: VCG
name: VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1
status: CLOSED
date: 2026-08-15
authority: VERSION_COMPATIBILITY_GOVERNANCE_BOUNDED_ARCHITECTURE_CONTRACT_V1
---

# VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1

## Base
- VERSION_GOVERNANCE_BASE_SHA = f8c707b81938326b2f1ed948ed96a99b1cb9d342 (#17 publication)
- VERSION_GOVERNANCE_BASE_TREE = 3bd78047f8e6de706386d5c1cb16a94be8d93427
- ROADMAP_17_SOURCE_RANGE_HASH_INVARIANT = PASS (preflight; no post-close correction needed —
  sourceRange participates in canonical digest via TimelineClip trimStart/trimEnd)

## Implementation
- VERSION_GOVERNANCE_IMPLEMENTATION_SHA = 5ac4854e46ca6e461ede7179f9c36b63912983b1
- VERSION_GOVERNANCE_IMPLEMENTATION_TREE = 4fc8e8479bbd6c9306e1bda48eeb677540a8535a
- VERSION_GOVERNANCE_PUBLICATION_SHA = (see git log)
- VERSION_GOVERNANCE_PUBLICATION_TREE = (see git log)

## Frozen principles
RELEASE_VERSION_IS_NOT_DATA_VERSION_IS_NOT_REVISION_ID_V1;
COMPATIBILITY_IS_AN_EXPLICIT_CONTRACT_RANGE_NOT_AN_INFERENCE_FROM_PRODUCT_VERSION_V1;
KERNEL_STYLE_STABLE_RELEASE_LINES_V1; VERSION_GOVERNANCE_GREENFIELD_CANONICALIZATION_V1;
EXECUTION_IS_PINNED_TO_RESOLVED_VERSIONS_V1;
ROLLOUT_SELECTS_A_VERSION_BUT_DOES_NOT_DEFINE_VERSION_SEMANTICS_V1;
EXECUTION_PROVENANCE_RECORDS_RESOLVED_VERSIONS_AND_ROLLOUT_CONTEXT_V1;
INTERFACE_VERSION_IS_NOT_PLATFORM_RELEASE_VERSION_V1;
INTERFACE_LIFECYCLE_IS_EXPLICIT_METADATA_NOT_VERSION_PARITY_V1;
BREAKING_INTERFACE_CHANGE_IS_MACHINE_GATED_V1;
TRANSPORT_SCHEMA_IS_A_PROJECTION_NOT_DOMAIN_AUTHORITY_V1.

## Type model (shared-kernel com.example.platform.shared.version)
ReleaseVersion (E.R.P) / CanonicalFormatVersion (E.R, single-segment rejected) /
VersionRange<T> (typed numeric; identity-scoped compatibility) / Lifecycle (DRAFT-PREVIEW-
STABLE-DEPRECATED-RETIRED, explicit) / CompatibilityAdvisory (bounded handling) /
ReleaseChannel (DEV-CANARY-PREVIEW-STABLE-LTS) / RolloutPolicy (id+revision, deterministic
cohort) / ExecutionProvenance (composable typed sections; BuildIdentity independent;
no god-object) / ApiContract (id + E.R + ApiLifecycle).

## API governance
OpenAPI 3.1.0 baseline (contracts/http/media-api/) = EXTERNAL TRANSPORT authority only;
Spectral 6.14.3 pinned ruleset (contract-id/lifecycle/operationId/error-shape); oasdiff
v1.28.0 pinned breaking-change gate with positive (additive) + negative (required property
removed) fixtures. Future tooling policy only: Buf/AsyncAPI/GraphQL Inspector/Pact DEFERRED.

## Greenfield canonicalization proof
legacy version parsers = 0; legacy version fields = 0; compatibility wrappers = 0;
dual write/read = 0; V1/V2 API dual track = 0; single-segment ContractVersion support = 0.
COMPATIBILITY_EVIDENCE_FOUND = NO.

## Verification
VersionGovernanceTest 14 PASS; full suite 7036 GREEN (0 failures/0 errors);
drift 70/70 (10 VG gates); Modulith PASS; bootJar PASS; pfirr1RemediationCheck PASS;
API governance gate 4/4 (spectral + oasdiff non-breaking + oasdiff breaking fixture).

## Deferred
Buf/AsyncAPI/GraphQL Inspector/Pact; CANONICAL_SCHEMA_EVOLUTION_V1 (later roadmap);
FIRST_REAL_MEDIA_CUT_V1 (NEXT_ACTION). Blocker = 0.
