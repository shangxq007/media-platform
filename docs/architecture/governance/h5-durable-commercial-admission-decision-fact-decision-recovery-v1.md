# H5 Durable Commercial Admission Decision Fact — Decision Recovery V1

Status: `H5_DURABLE_COMMERCIAL_ADMISSION_DECISION_DR_V1=FROZEN`

## 1. Authority, base, and scope

| Field | Frozen value |
|---|---|
| Task | `H5_DURABLE_COMMERCIAL_DECISION_FACT_DECISION_RECOVERY_V1` |
| Base commit | `f7fe6c0ab9b53694909ddb077420c0792b08e937` |
| Branch | `agent/h9-h5-commercial-read-revalidation-v1` |
| Owner | H5 commercial admission |
| Deliverable | This governance decision record only |
| Runtime/schema implementation | Not authorized |

The root `AGENTS.md` applies to the whole repository. It requires task-scoped
changes, exact repository evidence, preservation of unrelated work, and no
remote mutation without explicit authorization. The task packet is the newer,
narrower Owner authorization. There is no nested `AGENTS.md` beneath
`docs/architecture/governance/`, and no instruction conflict was found.

The initial worktree was clean at the exact base. One pre-existing stash,
`stash@{0}` from `agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery`,
was observed and left untouched.

## 2. Mechanical owner-reality scan

The scan covered production Java, canonical V1 schema, owner documentation,
and relevant tests. It used repository-wide `rg` searches for
`CommercialDecision`, `AccessDecision`, `EntitlementDecision`,
`QuotaDecision`, `PolicyDecision`, `FeatureFlagDecision`, `DecisionId`,
repositories, persistence, history, admission, authorization, receipts,
request/operation references, trace, correlation, and idempotency.

High-value current-reality findings are:

| Surface | Mechanical finding | Authority consequence |
|---|---|---|
| `shared-kernel/.../CommercialDecision.java` | Runtime value has principal, action, boolean result, one typed reason, evidence refs, authority version, trace, and time; it has no stable decision ID, request/operation identity, idempotency, validity contract, repository, or historical reader. | Useful semantic input, not durable fact authority. |
| `CommercialAdmissionRequest` / `CommercialAdmissionService` | Request has `traceId` and `decidedAt`; service composes Entitlement then Quota and returns an in-memory result. | Evaluation exists; durable admission identity and storage do not. |
| Admission callers | Exactly two production callers were found: `RenderJobSubmissionService` and `ClientExportService`. Their trace strings are correlation labels derived from project/tenant/preset and are not unique durable decision identity. | A trace must not become the primary key or replay key. |
| `docs/billing-access/02-access-decision.md` and corresponding Java | Transitional `AccessDecision` collapses entitlement, quota, and feature-flag material into a 17-field response. It has no decision ID or persistence. | It is neither a historical fact nor the new owner concept. |
| `docs/authorization/ACCESS_DECISION_DECONFLATION_MATRIX_V1.md` | Security `AuthorizationDecision` is independent and precedes product/commercial checks; feature flags are never security authority. | The H5 fact cannot replace or absorb security authorization. |
| Entitlement types/state | Two production `EntitlementDecision` records exist (shared commercial and entitlement transitional), plus mutable grant/override/bundle/tier state and entitlement command audit. | `CURRENT_ENTITLEMENT_STATE != HISTORICAL_ADMISSION_DECISION`. |
| Quota types/state | Two production `QuotaDecision` records exist, plus current `quota_usage` and durable quota mutation operations. | Quota state/consumption history is evidence, not the final past admission fact. |
| Policy and feature flags | `PolicyDecision` and `FeatureFlagDecision` are evaluation results. Feature-flag definitions/rules are current state; recent evaluation events are capped in memory and generic audit receives only a subset. | Definition/state/audit fragments cannot reconstruct the exact H5 decision. |
| Billing decision | `BillingDecisionService` generates a `decisionId` but stores decisions only in a `ConcurrentHashMap`; its loose details map and APPROVED/DENIED/PENDING status are not wired into H5 admission. | This is a noncanonical shadow, not reusable durable authority. |
| Generic audit | `audit_records` has no tenant column, typed principal/scope, ordered reason algebra, decision idempotency, validity, immutable decision contract, or owner-scoped fact query. | Audit is evidence/diagnostics, not commercial decision truth. |
| Canonical V1 schema | No commercial-admission or commercial-decision fact table exists. `entitlement_grant`, `entitlement_command_audit`, `quota_usage`, `quota_usage_operation`, feature-flag tables, `workflow_execution`, `wf_local_admission`, review decisions, and provider webhook receipts own other semantics. | No existing table is reusable. |
| Stable identities elsewhere | `shared-kernel` has `OperationRef(operationId, attemptId)`; operation, request, execution, job, trace, and idempotency identities occur across runtime and H5 tables. | Reuse those identities by reference when the caller has them; do not mint a parallel operation authority. |
| Tests | Current tests prove enum closure, structured evidence, allow/reason consistency, fail-closed admission, in-memory billing lookup, and projection-field preservation. No test proves restart-safe historical admission reads. | Present tests validate runtime values, not durability. |

The scan also found unrelated Render and Timeline types named `AccessDecision`
or carrying `decisionId`. Name similarity does not grant H5 commercial
authority and those types are excluded from reuse.

## 3. Owner answer

`H5_DURABLE_COMMERCIAL_DECISION_REQUIRED=YES`.

Later systems must be able to explain the exact H5 commercial/effective-access
decision that governed a past operation even after grants, quota usage,
policies, feature flags, subscriptions, billing workflow state, or human-facing
messages have changed. The durable owner truth is the decision actually made,
not a later reconstruction from current inputs.

The owner-selected concept is `CommercialAdmissionDecisionFact`. It is one
append-only H5 fact per idempotent admission attempt. It records the final H5
commercial outcome and bounded references to the authority evidence actually
consulted. It is not an `AccessDecision` god snapshot, an audit payload, an
authorization receipt, a current entitlement view, a quota ledger row, an H9
projection, or a copy of upstream objects.

## 4. Frozen distinctions

The following are normative:

```text
POLICY_DEFINITION != POLICY_EVALUATION
CURRENT_ENTITLEMENT_STATE != HISTORICAL_ADMISSION_DECISION
CURRENT_QUOTA_STATE != PAST_QUOTA_DECISION
CURRENT_FEATURE_FLAG != PAST_FEATURE_FLAG_DECISION
RECOMPUTED_DECISION != DURABLE_DECISION_FACT
COMMERCIAL_DECISION_READ_IS_A_FACT_READ_NOT_A_POLICY_REEVALUATION_V1
```

Also:

```text
SECURITY_AUTHORIZATION_DECISION != COMMERCIAL_ADMISSION_DECISION_FACT
EFFECTIVE_CAPABILITY_VIEW != COMMERCIAL_ADMISSION_DECISION_FACT
AUDIT_RECORD != COMMERCIAL_ADMISSION_DECISION_FACT
TRACE_ID != COMMERCIAL_ADMISSION_DECISION_ID
```

## 5. Stable identity and correlation

The stable identity is
`CommercialAdmissionDecisionFact.commercialAdmissionDecisionId`: an opaque,
immutable, H5-owned identifier assigned once. It contains no timestamp or
tenant/feature semantics. Historical lookup is always tenant-predicated even
if the identifier is globally collision-resistant. A timestamp, tenant ID,
principal ID, workspace ID, project ID, feature key, action, trace ID, row
offset, or any combination that omits the decision ID is not decision identity.

Idempotency is mandatory. `(tenantId, idempotencyKey)` uniquely identifies one
admission request within H5. A replay with the same request fingerprint returns
the same `commercialAdmissionDecisionId` and byte-equivalent owner truth. The
same key with a different fingerprint fails closed and never overwrites the
fact.

`admissionRequestRef` is the stable caller-owned request/invocation reference.
`operationRef` reuses the existing canonical operation/attempt identity when
one exists. A denial may precede creation of a domain operation, so
`operationRef` may be absent; `admissionRequestRef` and the H5 idempotency key
remain mandatory. A later operation may link to the decision by its decision
ID, but H5 does not create or redefine that operation. Current trace strings
remain diagnostic correlation only and cannot substitute for either reference.

## 6. Exact persisted fact contract

Every field in the persisted aggregate and owner read is classified below.
`REQUIRED_OWNER_TRUTH` means the field is part of the immutable authoritative
fact; “conditional” describes a strict semantic nullability rule, not a lower
classification.

| Field | Presence and semantics | Classification |
|---|---|---|
| `schemaVersion` | Required; version of this fact contract. | `REQUIRED_OWNER_TRUTH` |
| `commercialAdmissionDecisionId` | Required; opaque stable identity assigned once. | `REQUIRED_OWNER_TRUTH` |
| `principalRef.tenantId` | Required tenant boundary. | `REQUIRED_OWNER_TRUTH` |
| `principalRef.principalType` | Required closed canonical type: USER, SERVICE_ACCOUNT, WORKSPACE, or ORGANIZATION. | `REQUIRED_OWNER_TRUTH` |
| `principalRef.principalId` | Required ID scoped beneath the tenant. | `REQUIRED_OWNER_TRUTH` |
| `scope.workspaceId` | Conditional; required when admission is workspace-scoped. | `REQUIRED_OWNER_TRUTH` |
| `scope.organizationId` | Conditional; required when admission is organization-scoped. | `REQUIRED_OWNER_TRUTH` |
| `scope.projectId` | Conditional; required when the requested operation/resource is project-scoped. | `REQUIRED_OWNER_TRUTH` |
| `subjectRef.subjectType` | Required typed identity of the subject whose commercial access was evaluated; may reference the principal but is not inferred on read. | `REQUIRED_OWNER_TRUTH` |
| `subjectRef.subjectId` | Required stable subject reference within the tenant/scope. | `REQUIRED_OWNER_TRUTH` |
| `resourceRef.resourceType` | Conditional pair; required when admission targets an existing resource. | `REQUIRED_OWNER_TRUTH` |
| `resourceRef.resourceId` | Conditional pair; must belong to the recorded tenant/project/workspace. | `REQUIRED_OWNER_TRUTH` |
| `featureKey` | Required canonical feature/capability/product-access target consulted by H5. | `REQUIRED_OWNER_TRUTH` |
| `action` | Required canonical attempted action. | `REQUIRED_OWNER_TRUTH` |
| `outcome` | Required closed enum: `ALLOW` or `DENY`. | `REQUIRED_OWNER_TRUTH` |
| `orderedReasons[].ordinal` | Required zero-based order, contiguous and unique within the decision. | `REQUIRED_OWNER_TRUTH` |
| `orderedReasons[].code` | Required closed reason algebra defined in section 7. | `REQUIRED_OWNER_TRUTH` |
| `evidenceRefs[].ordinal` | Required bounded order of evidence actually consulted. | `REQUIRED_OWNER_TRUTH` |
| `evidenceRefs[].authorityKind` | Required closed kind: ENTITLEMENT, QUOTA, POLICY, FEATURE_FLAG, SUBSCRIPTION, BILLING_ACTION, or PAYMENT_WORKFLOW. | `REQUIRED_OWNER_TRUTH` |
| `evidenceRefs[].evidenceType` | Required typed evidence category within that authority. | `REQUIRED_OWNER_TRUTH` |
| `evidenceRefs[].evidenceId` | Required stable reference to evidence used; never an embedded upstream object. | `REQUIRED_OWNER_TRUTH` |
| `evidenceRefs[].authorityVersion` | Required version/revision of the evaluating authority or evidence contract. | `REQUIRED_OWNER_TRUTH` |
| `evidenceRefs[].contribution` | Required closed value `DECISIVE` or `SUPPORTING`. | `REQUIRED_OWNER_TRUTH` |
| `decidedAt` | Required authority decision instant. | `REQUIRED_OWNER_TRUTH` |
| `validFrom` | Required; equals `decidedAt` for V1. | `REQUIRED_OWNER_TRUTH` |
| `validUntil` | Optional earliest asserted expiry/validity bound among the deciding inputs. Absence never means reusable forever. | `REQUIRED_OWNER_TRUTH` |
| `admissionRequestRef` | Required stable caller-owned request/invocation reference. | `REQUIRED_OWNER_TRUTH` |
| `operationRef.operationId` | Conditional; present when a canonical operation already exists. | `REQUIRED_OWNER_TRUTH` |
| `operationRef.attemptId` | Conditional; present when the decision governs a particular attempt. | `REQUIRED_OWNER_TRUTH` |
| `idempotencyKey` | Required tenant-scoped replay key. | `REQUIRED_OWNER_TRUTH` |
| `requestFingerprint` | Required canonical fingerprint used to reject key/payload mismatch. | `REQUIRED_OWNER_TRUTH` |
| `authorityVersion` | Required version of the H5 commercial admission composition. | `REQUIRED_OWNER_TRUTH` |
| `recordedAt` | Required persistence timestamp; never used to order policy or infer the decision time. | `NON_AUTHORITATIVE_DIAGNOSTIC` |
| `traceId` | Optional distributed-tracing correlation; never identity or semantic authority. | `NON_AUTHORITATIVE_DIAGNOSTIC` |
| `explanation` | Optional owner-read rendering derived only from outcome, typed reasons, and resolvable evidence labels; not persisted as truth. | `DERIVED_EXPLANATION` |
| `diagnosticMessage` | Optional restricted diagnostic text; may be persisted only with secret/PII controls and is never the sole reason. | `NON_AUTHORITATIVE_DIAGNOSTIC` |

No other loose map, request context, provider payload, policy object, feature-flag
details object, entitlement snapshot, quota graph, pricing object, current tier,
upgrade recommendation, or human message is part of V1 owner truth. Adding a
persisted or owner-read field requires a new H5 governance decision that assigns
one of the three classifications above.

## 7. Closed typed ordered reason algebra

`H5_DURABLE_REASON_REQUIRED=YES`.

V1 reuses the accepted generic `CommercialDecisionReason` algebra exactly:

```text
ALLOWED
NOT_ENTITLED
POLICY_DENIED
QUOTA_EXCEEDED
SUBSCRIPTION_INACTIVE
COMMERCIAL_ACCOUNT_SUSPENDED
BILLING_ACTION_REQUIRED
PAYMENT_FAILED
TRIAL_EXPIRED
```

The list is non-empty. `ALLOW` requires exactly `[ALLOWED]`. `DENY` forbids
`ALLOWED` and requires one or more denial codes. Ordinal zero is the decisive
primary reason; any later reason is an actually observed, deterministic
secondary contributor, not a reason recomputed during read. Each decisive
reason must have at least one `DECISIVE` evidence reference. Provider-native
statuses and human text cannot enter the algebra.

Feature flags remain product/feature evidence and never security authority.
When a flag contributes to H5 denial through accepted policy composition, the
closed final reason is `POLICY_DENIED` and the feature-flag evaluation is
referenced as typed evidence; V1 does not add `FEATURE_FLAG_DISABLED` to the
accepted H5 reason set.

## 8. Evidence boundary and non-god-snapshot rule

Evidence references record only authorities actually evaluated for this
decision. Entitlement, Quota, Policy, and Feature Flag references are explicit
when those authorities participated; Subscription, Billing Action, and Payment
Workflow references are allowed only for their existing H5 denial semantics.
Each reference captures identity, type, version, ordering, and contribution,
not the upstream row, graph, object, provider payload, or current state.

At least one evidence reference is required. The implementation must impose a
small configured maximum and reject overflow rather than silently truncate;
the exact storage limit belongs to the later H5 schema implementation. Evidence
resolution may enrich an explanation, but an unavailable or changed upstream
object never changes the stored decision and never makes the fact unreadable.

## 9. Validity and historical semantics

The fact proves what H5 decided for its bound admission request/operation. It
is not a bearer token. `validUntil` records the decision's asserted operational
validity boundary; expiration does not erase or falsify historical truth, and
absence of an expiry does not authorize reuse. Every new execution admission
still obtains a fresh idempotent decision as required by its application flow.

Historical reads return the stored outcome, reasons, scope, correlations, and
evidence references exactly as recorded. They never evaluate current grants,
usage, policy, feature flags, subscription, billing, payment, runtime
availability, or authorization. A separately requested “what would happen
now” evaluation is a new operation with a new decision identity and must not be
presented as the historical fact.

## 10. Tenant isolation and application read boundary

`H5_APPLICATION_READ_BOUNDARY_REQUIRED=YES`.

The required owner boundary is
`CommercialAdmissionDecisionFactQuery`, implemented in H5 and backed by a
subordinate tenant-predicated repository. It may expose only bounded queries:

1. get one fact by `(tenantId, commercialAdmissionDecisionId)`;
2. get facts by `(tenantId, admissionRequestRef)`;
3. get facts by `(tenantId, operationRef)` with bounded pagination.

There is no global list, unrestricted ID-only lookup, row-offset identity,
arbitrary evidence search, or raw-table/repository access for H9. H9 and other
consumers receive this owner-scoped application read or a narrower projection;
their field wishes do not define the H5 schema.

Every read fails closed unless all applicable relations hold:

- the authenticated tenant equals `principalRef.tenantId`;
- workspace, organization, project, subject, resource, request, and operation
  references belong to that tenant and to each other;
- a principal reading its own decision matches the recorded principal and has
  current security authorization to the referenced resource;
- tenant commercial administrators and tenant auditors have explicit scoped
  roles and current security authorization;
- internal services use an explicitly granted tenant-scoped service identity;
- cross-tenant support/audit requires a separate named system authority not
  present in V1, so it is denied by this boundary.

Current authorization controls who may read the historical fact; it never
recomputes or edits the historical commercial outcome. Human explanation and
diagnostics may be redacted independently without redacting required owner
truth from an otherwise authorized reader.

## 11. Persistence disposition

No current table satisfies the fact contract. Reusing `audit_records` would
turn loosely typed diagnostics into commercial authority. Reusing entitlement,
quota, feature-flag, workflow-local-admission, review-decision, webhook-receipt,
or usage tables would collapse distinct ownership. The in-memory billing and
feature-flag stores are shadows and disappear on restart.

The later implementation therefore requires an additive, append-only H5
decision-fact schema with typed ordered reason and evidence storage,
tenant/idempotency uniqueness, tenant-predicated indexes, and mutation
rejection. Physical DDL and jOOQ generation are deliberately deferred; this
document freezes the owner contract, not an H9-designed table shape.

```text
H5_DURABLE_DECISION_SCHEMA_CHANGE_REQUIRED=YES
H5_EXISTING_TABLE_REUSABLE=NO
H5_EXISTING_RAW_DECISION_AUTHORITY=SHADOW
IMPLEMENTATION=WAIT_FOR_CENTRAL_V1_JOOQ_LOCAL_LOCK_RELEASE
```

## 12. Exact frozen decision

```text
H5_DURABLE_COMMERCIAL_DECISION_REQUIRED=YES
H5_DURABLE_REASON_REQUIRED=YES
H5_DECISION_IDENTITY=CommercialAdmissionDecisionFact.commercialAdmissionDecisionId
H5_DURABLE_DECISION_SCHEMA_CHANGE_REQUIRED=YES
H5_EXISTING_TABLE_REUSABLE=NO
H5_EXISTING_RAW_DECISION_AUTHORITY=SHADOW
H5_APPLICATION_READ_BOUNDARY_REQUIRED=YES
IMPLEMENTATION=WAIT_FOR_CENTRAL_V1_JOOQ_LOCAL_LOCK_RELEASE
```

## 13. Scope and count freeze

This task changes governance documentation only. It does not authorize or
claim implementation.

```text
PRODUCTION_SOURCE_CHANGE_COUNT=0
SCHEMA_CHANGE_COUNT=0
JOOQ_CHANGE_COUNT=0
H5_DURABLE_DECISION_IMPLEMENTATION_COUNT=0
H9_PRODUCTION_PROJECTION_IMPLEMENTATION_COUNT=0
H7_MODIFICATION_COUNT=0
```

No production source, test source, build file, configuration, V1 migration,
jOOQ/generated source, persistence implementation, H9 projection, H7 path,
provider runtime identity, remote ref, or integration state is modified by
this decision recovery.
