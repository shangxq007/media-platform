# H10-R1 — Render Request Initiator Authority Reconciliation V1

STATUS: IMPLEMENTATION_CANDIDATE_PENDING_INDEPENDENT_REVIEW

## Frozen decision

```text
H10_INDEPENDENT_BLOCKER_REVIEW=PASS
H10_IMPLEMENTATION_PROGRESS=ACCEPTED
H10_FINAL_ACCEPTANCE=NO
H10-BLOCKER-RECIPIENT-001=VALID
BLOCKER_CLASSIFICATION=CROSS_AUTHORITY_MISSING_CONTRACT
H10_NOTIFICATION_ARCHITECTURE_CORRECTION_REQUIRED=NO
RENDER_NOTIFICATION_DEFAULT_AUDIENCE_IS_REQUESTING_PRINCIPAL_V1=FROZEN
RENDER_OWNS_REQUEST_INITIATOR_PROVENANCE_V1=FROZEN
IDENTITY_OWNS_PRINCIPAL_IDENTITY_V1=FROZEN
NOTIFICATION_OWNS_EVENT_TO_AUDIENCE_MAPPING_V1=FROZEN
PROJECT_ID_IS_NOT_A_NOTIFICATION_RECIPIENT_V1=FROZEN
TENANT_ID_IS_NOT_A_NOTIFICATION_RECIPIENT_V1=FROZEN
NO_ARBITRARY_TENANT_USER_FALLBACK_V1=FROZEN
SYSTEM_INITIATED_RENDER_MUST_NOT_FABRICATE_A_HUMAN_RECIPIENT_V1=FROZEN
```

## Canonical base provenance

Local `main` was `9f1e06a6907fac94efcd947285f94eaa3f4207bf`, tree `66fab954970de584b5ff55804aaff5ee7b2f228e`, but fetched `origin/main` remained `3aa44ff7e8615ff9977fba4e90880bd7e9207b16`, tree `a3cc107575b0e231f4b9b1e5b986dd41a9456488`.

`9f1e06a` is a one-parent local Phase19 verifier lifecycle correction (`parent=75a64101b815d0e7d6a7c96f9b3c67047f2f5c31`, tree `66fab...`). Its correction itself was locally accepted, but the enclosing Phase20 canonical-integration Stage A failed on the pre-existing TaskG scan-universe verifier and publication was withheld. It is reachable only from local `main`, is not contained in `origin/main`, and was never pushed.

```text
9F1E06A_PROVENANCE=ACCEPTED_LOCAL_NOT_PUSHED
LATEST_ACCEPTED_CANONICAL_BASE=origin/main
R1_BASE_SHA=3aa44ff7e8615ff9977fba4e90880bd7e9207b16
R1_BASE_TREE=a3cc107575b0e231f4b9b1e5b986dd41a9456488
```

## Base inventory

```text
EXISTING_RENDER_INITIATOR_AUTHORITY=NO
EXISTING_TYPED_PRINCIPAL_IDENTITY=CanonicalActor
RENDER_SUBMISSION_HAS_AUTHENTICATED_PRINCIPAL=YES_AVAILABLE_NOT_CAPTURED_AT_BASE
EXISTING_GENERIC_ACTOR_ABSTRACTION=CanonicalActor
RENDER_INITIATOR_AUTHORITY_REQUIRES_ARCHITECTURE_ESCALATION=NO
```

The accepted base already contains `CanonicalActor`, `ActorType`, `CanonicalActorResolver`, request-attribute and MDC resolvers, a primary composite resolver, and explicit `SystemCanonicalActorResolver`. H10-R1 reuses those authorities and introduces no `PrincipalId`, `ActorId`, email identity, or provider subscriber identity.

## Implementation

- Added sealed immutable `RenderInitiator` with Principal and System variants, snapshotting only canonical actor ID, actor type, and tenant scope.
- Render submission ports require initiator separately from user-controlled JSON.
- HTTP boundaries resolve `CanonicalActor` and reject missing actor or tenant mismatch.
- V1 `render_job` now has non-null `initiator_type`, `initiator_id`, and `initiator_tenant_id`.
- Accepted, rejected, normal completion/failure, stale compensation, delivery reconstruction, retry, recovery, and revision-pinning paths preserve the snapshot.
- Retry uses `INSERT ... SELECT`; no ambient completion-time actor can replace provenance.
- Completed/failed events carry one typed initiator.
- Render's production Notification dependency and `NotificationEventPublisher` callers were removed; local `ApplicationEventPublisher` remains the bounded local event mechanism in this dependency lane.
- No Notification audience mapping or Novu behavior was introduced.

## Authority and behavior

```text
RENDER_REQUEST_INITIATOR_CAPTURED=PASS
RENDER_INITIATOR_IMMUTABLE=PASS
RENDER_EVENT_INITIATOR_PRESERVED=PASS
TENANT_PROJECT_PRINCIPAL_SCOPE_VALIDATED=PASS
SYSTEM_RENDER_FAKE_PRINCIPAL_COUNT=0
RENDER_TO_NOTIFICATION_PRODUCTION_DEPENDENCY_COUNT=0
RENDER_NOVU_REFERENCE_COUNT=0
IDENTITY_NOVU_REFERENCE_COUNT=0
PROJECT_ID_AS_SUBSCRIBER_COUNT=0
TENANT_ID_AS_SUBSCRIBER_COUNT=0
ARBITRARY_TENANT_USER_FALLBACK_COUNT=0
DUPLICATE_PRINCIPAL_ID_AUTHORITY_COUNT=0
CURRENT_AMBIENT_ACTOR_AT_COMPLETION_COUNT=0
RENDER_INITIATOR_SCHEMA_COLUMN_COUNT=3
RENDER_COMPLETED_EVENT_INITIATOR_FIELD_COUNT=1
RENDER_FAILED_EVENT_INITIATOR_FIELD_COUNT=1
UNCLASSIFIED=0
```

## Gates

```text
ARCHITECTURE_NEGATIVE_CONTROLS=PASS (15/15)
H10_R1_ARCHITECTURE_GUARD=PASS
TARGETED_TESTS=PASS (207 tests, 0 failures, 0 errors, 0 skipped)
MODULITH_GATE=PASS (92/92 tasks, 1m 9s)
FULL_SERIAL_GRADLE_SUITE=PASS (8063 tests, 0 failures, 0 errors, 29 skipped; 200/200 tasks; 21m 22s)
BOOTJAR=PASS
PFIRR1_REMEDIATION_CHECK=PASS
ARCHITECTURE_DRIFT=PASS
CI_COMPILE=PASS
EXACT_DOCKERFILE_IMAGE_BUILD=PASS (inner bootJar 89/89 tasks, image localhost/media-platform:h10-r1-ci)
CI_EQUIVALENT=PASS
```

The full serial run initially exposed 10 legacy real-TCP fixtures that disabled security and therefore supplied no actor. Tests were corrected with explicit scoped `@MockitoBean CanonicalActorResolver` identities; production fail-closed behavior was not weakened. A direct Render farm fixture was also corrected to persist an explicit SYSTEM initiator. The clean rerun passed.

## Boundary and next step

This candidate establishes only the cross-authority prerequisite. It does not resolve H10 itself, merge into canonical main, or mutate the existing H10 branch. After independent acceptance and canonical integration, the existing H10 branch must merge the latest accepted canonical main with an explicit merge commit, preserving every accepted H10 SHA. Only then may Notification consume the initiator and prove the production Novu vertical.
