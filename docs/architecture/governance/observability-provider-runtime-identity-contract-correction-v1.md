---
type: bounded-architecture-contract-correction
name: OBSERVABILITY_PROVIDER_RUNTIME_IDENTITY_CONTRACT_CORRECTION_V1
status: FROZEN
date: 2026-08-30
parent: 943657fcd0d53583b8d6e9b09f3f0e5cd6a1d743
parent_tree: f255a78c4dec2b16cc46f020e73cde9af10349f4
authority: H9_PROVIDER_RUNTIME_IDENTITY_CONTRACT_CORRECTION_V1
implementation_authorization: NO_GO
---

# Observability provider runtime identity contract correction V1

```text
H9_CORRECTION_RUN_MARKER=H9_PROVIDER_RUNTIME_IDENTITY_CORRECTION_CODEX_20260830_1
CORRECTION_MODE=APPEND_FORWARD_DECISION_CONTRACT_ONLY
ORIGINAL_H9_CONTRACT=PRESERVED
ORIGINAL_H9_LEDGER=PRESERVED
H9_PROJECTION_COUNT=7
H9_RUNTIME_AUTHORITY_COUNT=0
H9_COMMERCIAL_AUTHORITY_COUNT=0
UNCLASSIFIED=0
DUPLICATE_AUTHORITY_COUNT=0
IMPLEMENTATION_AUTHORIZATION=NO_GO
```

This append-forward child correction supersedes only the invalid mandatory
`ProviderRuntimeBundleId` dependency in
`observability-typed-projection-bounded-architecture-contract-v1.md`. Every
non-conflicting law, boundary, projection name, source fact, and prohibition in
the accepted H9 contract and its disposition ledger remains frozen. This record
does not modify either original H9 Decision Recovery file, authorize an H1/H5
read port, implement an H9 projection, or create runtime or commercial truth.

## 1. Corrective laws

The following laws are frozen verbatim:

1. `MATERIAL_ENTITY_BEFORE_STABLE_IDENTITY_V1`.
2. `NO_PROVIDER_RUNTIME_BUNDLE_ID_WITHOUT_MATERIAL_BUNDLE_ENTITY_V1`.
3. `H9_MUST_NOT_MATERIALIZE_MISSING_RUNTIME_ENTITY_V1`.
4. `PROVIDER_RUNTIME_VIEW_PROJECTS_EXISTING_H1_FACTS_V1`.
5. `RUNTIME_DEPENDENCY_FINGERPRINT_IS_NOT_INSTALLED_BUNDLE_IDENTITY_V1`.
6. `PROVIDER_BINDING_PIN_IS_NOT_INSTALLED_BUNDLE_IDENTITY_V1`.
7. `PROVIDER_BINDING_PIN_PLUS_RUNTIME_DEPENDENCY_FINGERPRINT_IS_CORRELATION_NOT_NEW_IDENTITY_V1`.

The current identity disposition is frozen:

```text
PROVIDER_RUNTIME_BUNDLE_ID_CURRENT_STATUS=DEFERRED_CONDITIONAL_IDENTITY_NOT_AUTHORIZED
CREATE_PROVIDER_RUNTIME_BUNDLE_ID=NO
PROVIDER_RUNTIME_BUNDLE_ID=NO_GO
```

No material installed-bundle entity, independent addressing need, allocation
authority, lifecycle, or persistence contract exists in the accepted H1
authority or repository reality. A stable identifier cannot precede that
entity. H9 is a derived read boundary and cannot create it to satisfy a view.

## 2. Exact append-forward supersession

This record has later authority only where the accepted H9 contract makes
`ProviderRuntimeBundleId` mandatory:

- Section 5 lines 174-178 no longer include `ProviderRuntimeBundleId` among H1
  freeze inputs required by H9. The Section 5 table row at line 185 is
  superseded in full. H9 has no allowed bundle-ID consumer and no bundle-ID
  composition authority.
- Section 7.1 lines 230-238 are superseded only for the purpose phrase “which
  provider runtime bundle is being considered” and the required
  `ProviderRuntimeBundleId/reference`. `ProviderRuntimeView` instead answers
  which exact executable binding and observed dependency/conformance evidence
  are being considered.
- Any other original H9 phase or guard language that treats
  `ProviderRuntimeBundleId` as an unconditional H1 freeze prerequisite or an H9
  required dependency is superseded only to that extent. Prohibitions on H9
  defining upstream H1 facts remain in force.

Corrected `ProviderRuntimeView` content is:

- exact existing `ProviderBindingPin`;
- existing H1 `RuntimeDependencyRequirement` reference;
- existing H1 `RuntimeDependencyObservation` reference, outcome, observed-at,
  freshness, and source context;
- existing H1 `RuntimeDependencyFingerprint` reference;
- existing H1 `RuntimeEligibilityDecision` and ordered reasons;
- bounded diagnostic references and the existing canonical H1 facts required
  by those exact view semantics.

`ProviderBindingPin` and `RuntimeDependencyFingerprint` are consumed as
separate, independently typed facts. Their co-occurrence is correlation for one
view; it is not an entity, identifier, value object, allocation key, persistence
key, or lifecycle authority.

The following substitutes are forbidden:

- `ProviderRuntimeBundleId(hash(...))`;
- `ProviderRuntimeBundleKey`;
- `InstalledBundleKey`;
- `RuntimeBundleRef`;
- a synthetic `String` bundle ID;
- a `tuple-as-domain-ID` substitute;
- any H9-local bundle identity.

```text
PROVIDER_RUNTIME_BUNDLE_ID_REQUIRED_BY_H9=NO
PROVIDER_RUNTIME_BUNDLE_ID_CURRENT_AUTHORIZATION=DEFERRED_CONDITIONAL
PROVIDER_RUNTIME_BUNDLE_ID_MATERIALIZATION_ACTION=NONE
PROVIDER_RUNTIME_BUNDLE_ID_DEFINITION_COUNT=0
PROVIDER_RUNTIME_BUNDLE_ID_REQUIRED_DEPENDENCY_COUNT=0
```

## 3. Three distinct authorities

The following inequality is frozen:

```text
ProviderImplementationId != ProviderBindingPin != RuntimeDependencyFingerprint
```

`ProviderImplementationId` identifies one stable provider implementation
strategy/behavior under a provider family. It is one component of, and is not
equal to, the aggregate executable binding.

`ProviderBindingPin` remains the exact existing single immutable provider
executable-binding authority. Its fields remain exactly:

1. `ProviderId providerId`;
2. `ProviderImplementationId providerImplementationId`;
3. `ProviderVersion providerVersion`;
4. `ProviderExecutionContractVersion providerExecutionContractVersion`;
5. `ProviderCapabilityProfileVersionOrDigest providerCapabilityProfileVersionOrDigest`;
6. canonical ordered `List<CapabilityImplementationId> capabilityImplementationPins`.

Its existing semantics remain unchanged: every component and capability-pin
element is non-null; duplicate capability pins are rejected; pins are sorted by
canonical value and stored as an immutable copy; equality and hashing are Java
record component semantics after canonicalization. No field or invariant is
added. It identifies the immutable executable binding and implies neither an
installed-bundle identity nor install/activate/retire lifecycle.

`RuntimeDependencyFingerprint` remains the H1-owned deterministic operational
fingerprint of an exact dependency observation and runtime binding. It
represents observed dependency/conformance evidence: provider implementation,
worker runtime, optional device, probe schema, dependency versions, ABI,
features, and build/runtime flags. It can change with a new observation closure.
It is not an installed-bundle identifier, registry key, allocation identity,
or lifecycle record.

Combining the immutable binding with an observation-derived fingerprint helps
an operator correlate what was bound with what was observed. The tuple does not
acquire domain identity, equality/allocation semantics, persistence, or
lifecycle merely because a projection displays both values.

## 4. Conditional future H1 decision only

The future trigger is frozen exactly:

> Only future independent H1 Decision Recovery may introduce
> `ProviderRuntimeBundleId` when one runtime incarnation supports multiple
> stable installed bundles requiring independent addressing, identity persists
> meaningfully across observations/fingerprint changes, lifecycle exists,
> provenance/rollback/audit needs direct entity reference, and existing
> identities cannot represent it without loss.

Every condition is mandatory. Packaging variety, a changed digest, one runtime
observation, a convenient join key, or an H9 display need is insufficient.

Before any future authorization, that independent H1 Decision Recovery must
freeze all of the following:

1. exact entity fields and which fields are identity-bearing;
2. constructor and cross-field invariants, including runtime-incarnation scope;
3. canonical representation and serialization;
4. equality and hash semantics across observation and fingerprint changes;
5. identifier allocation authority, uniqueness scope, collision behavior, and
   prohibition on H9 allocation;
6. lifecycle states, allowed transitions, installation/activation/retirement
   ownership, and deletion/retention rules;
7. persistence schema, durable reference semantics, lookup/read boundary, and
   provenance/rollback/audit retention;
8. relationships and cardinalities to `ProviderId`,
   `ProviderImplementationId`, `ProviderBindingPin`, `WorkerRuntimeId`, exact
   runtime incarnation, optional `DeviceId`, `RuntimeDependencyObservation`,
   `RuntimeDependencyFingerprint`, eligibility decisions, and mutable
   freshness;
9. whether and how a bundle identity survives restart, re-probe, dependency
   patching, fingerprint change, runtime reincarnation, migration, and rollback;
10. proof that existing identities and direct fact correlation cannot represent
    the material entity without semantic or audit loss.

Until all items are independently frozen and implementation is separately
authorized, the status remains deferred conditional and materialization remains
none.

## 5. Projection and authority preservation

Exactly seven H9 projection names remain frozen, with no rename, addition,
removal, merge, or split:

1. `ProviderRuntimeView`;
2. `WorkerRuntimeView`;
3. `DeviceView`;
4. `ExecutionView`;
5. `RenderProgressView`;
6. `CompatibilityExplanationView`;
7. `RuntimeFailureView`.

The corrected machine-readable authority is
`observability-runtime-projection-dependency-matrix-correction-v1.tsv`. It
preserves the prior seven-row source facts while removing the invalid mandatory
bundle dependency. Every row is a derived projection, adds zero H9 runtime
authorities and zero H9 commercial authorities, is classified, and creates no
duplicate authority.

No production, test, build, configuration, schema, migration, generated,
H1/H5 read-port, H9 projection, UI, or runtime implementation is authorized by
this correction.
