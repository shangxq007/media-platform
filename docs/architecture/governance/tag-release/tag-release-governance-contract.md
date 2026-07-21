# Tag and Release Governance Contract

## 1. Authority

This document is the **sole authority** for tag naming, tag typing, tag message format, tag immutability rules, and remote publication boundaries for all governance tags in this repository.

**Authority chain position:** L1 (Governance Decision)

**Source-of-truth registration:** `docs/architecture/governance/source-of-truth/source-of-truth-matrix.json` → `tag-release` domain.

**Registry:** `docs/architecture/governance/tag-release/governance-tag-registry.json`

---

## 2. Tag Namespace

All architecture governance decision tags MUST use the namespace:

```
governance/
```

No other namespace is authorized for governance decision tags.

---

## 3. Tag Naming Convention

### 3.1 Name Structure

Every new governance tag MUST follow:

```
governance/<subject>-<decision-state>
```

### 3.2 Naming Rules

| Rule | Requirement |
|------|-------------|
| Case | `lowercase` only |
| Style | `kebab-case` |
| Character set | ASCII only |
| Task numbers | PROHIBITED in tag name |
| Agent names | PROHIBITED in tag name |
| Automatic conversion from task name | PROHIBITED |
| `process-only` suffix | PROHIBITED |
| `tag-finalization` as decision-state | PROHIBITED |

### 3.3 Registry Requirement

A tag name MUST have a precise entry in the `governance-tag-registry.json` before the tag object may be created.

Convention, pattern matching, or historical precedent CANNOT substitute for registry authorization.

---

## 4. Tag Type

### 4.1 Annotated Requirement

All governance tags MUST be **annotated** tags.

### 4.2 Signature Status

| Property | Status |
|----------|--------|
| Signed tag | NOT REQUIRED |
| Lightweight tag | PROHIBITED |

Signed tags will be required only after signing keys, identity verification, and key rotation contracts are established. Until then, annotated-only is the standard.

---

## 5. Tag Message Contract

### 5.1 General Format

Tag messages MUST include:

1. A human-readable decision header
2. `Target:` field with the full 40-character commit SHA
3. `Decision:` field with the machine-readable decision identifier
4. `Contract:` field referencing the governing contract
5. `Verification:` field referencing the verification task
6. `Decision-Date:` field in `YYYY-MM-DD` format

### 5.2 Prohibited Content

- Local `/tmp` paths
- Agent execution names as governance decision identifiers
- Unverifiable claims about acceptance status

---

## 6. Tag Immutability

### 6.1 Accepted and Published Tags

Once a governance tag has been:

1. Created with an authorized name from the registry
2. Pointing to the correct target commit
3. With the correct annotated message

The tag is **immutable**:

- MUST NOT be force-updated
- MUST NOT be deleted and recreated with the same name
- MUST NOT have its annotated message modified
- MUST NOT have its peeled commit changed

### 6.2 Error Correction

When an error is discovered in an accepted governance tag:

- Rewriting the published governance tag is PROHIBITED
- A new, explicitly authorized correction decision must be created
- Whether to create a replacement tag is authorized by a separate governance task

### 6.3 Provisional Local Tags

Tags that are:

- Not yet authorized by the registry
- Not yet pushed to remote

MAY be deleted under a precise **Tag Correction Task** that:

1. Verifies the remote does not contain the tag
2. Deletes the provisional local tag
3. Creates the authorized replacement tag

---

## 7. Remote Publication Boundary

### 7.1 Distinction

| Phase | Meaning |
|-------|---------|
| **Local Tag Finalization** | Tag object created in local repository |
| **Remote Tag Publication** | Tag pushed to a remote |

Tag creation success DOES NOT equal publication.

### 7.2 Remote Push Authorization

Remote tag push MUST be an independently authorized task.

### 7.3 Prohibited Actions

- Tag agent pushing tags without explicit authorization
- `git push --tags` (batch push of unrelated tags)
- Force push of any tag

### 7.4 Pre-Push Verification

Before remote publication, ALL of the following MUST be verified:

1. Exact tag name matches registry entry
2. Tag object type is `annotated`
3. Tag message matches the authorized message
4. Peeled commit matches the authorized target
5. No remote conflict (tag does not already exist on remote)
6. Governance acceptance status is confirmed

---

## 8. Legacy Tags

The following tags were created before this contract existed:

| Tag | Status |
|-----|--------|
| `governance/execution-stack-simplification-accepted` | LEGACY_GOVERNANCE_TAG |
| `governance/greenfield-baseline-consolidated` | LEGACY_GOVERNANCE_TAG |

These legacy tags:

- MUST be preserved as existing governance markers
- Do NOT automatically define future naming rules
- All future new governance tags MUST enter the registry first
- This contract does NOT retrospectively modify their tag objects or messages

---

## 9. Current Greenfield Baseline Authorization

### 9.1 Authorized Tag

| Property | Value |
|----------|-------|
| **Tag name** | `governance/greenfield-baseline-accepted` |
| **Subject** | `greenfield-baseline` |
| **Decision state** | `accepted` |
| **Target commit** | `673e180a3236d747b4fd2aaaa5ca7a11a0cf830d` |
| **Tag type** | `annotated` |
| **Signature** | NOT REQUIRED |

### 9.2 Authorized Message

```
GREENFIELD BASELINE ACCEPTED

Target: 673e180a3236d747b4fd2aaaa5ca7a11a0cf830d
Decision: GREENFIELD_BASELINE_REVERIFICATION_6_PASSED
Contract: ARCH-CODE-GOV-GREENFIELD-BASELINE-SCHEMA-CONTRACT-AMENDMENT.2A-CONTRACT.2
Verification: ARCH-CODE-GOV-GREENFIELD-BASELINE-REVERIFICATION.6
Decision-Date: 2026-07-21
```

### 9.3 Decision Meaning

This tag signifies:

> GREENFIELD BASELINE REVERIFICATION.6 has passed and received governance acceptance.

### 9.4 Target Commit Relationship

The authorized baseline tag target is `673e180a3236d747b4fd2aaaa5ca7a11a0cf830d`.

This contract commit is a subsequent governance control-plane document and does NOT alter the already-verified baseline payload.

This contract's creation:

- MUST NOT reopen DG-001
- MUST NOT reopen CONTRACT.2
- MUST NOT alter REVERIFICATION.6 result
- MUST NOT require re-running REVERIFICATION.6

---

## 10. Provisional Tag Classification

### 10.1 Classified Tag

| Property | Value |
|----------|-------|
| **Tag name** | `governance/greenfield-baseline-tag-finalization` |
| **Status** | `UNACCEPTED_LOCAL_PROVISIONAL_TAG` |

### 10.2 Classification Rationale

- Name was not authorized by registry
- Name was derived from task name
- Message was not defined by governance contract
- Never pushed to remote

### 10.3 Constraints

This tag:

- Is NOT an accepted governance tag
- MUST NOT be pushed
- MUST NOT be treated as a release or baseline identifier
- MUST NOT be deleted, moved, or overwritten by this task

Only a dedicated **Tag Correction Task** may, after verifying the remote does not contain this tag:

1. Delete the provisional local tag
2. Create the precisely authorized replacement tag

---

## 11. Contract Update Process

Changes to this contract require:

1. An architecture governance task with explicit authorization
2. Registry update in `governance-tag-registry.json`
3. Source-of-truth matrix update if authority boundaries change

---

## 12. Source-of-Truth Alignment

| Authority | Document |
|-----------|----------|
| Tag naming authority | `tag-release-governance-contract.md` (this document) |
| Exact governance tag registry | `governance-tag-registry.json` |

ADR decisions, task prompts, historical tags, and agent reports are NOT the final authority source for tag governance.

---

## 13. Quality-Passing Tag Authorization

### 13.1 Authorized Tag

| Property | Value |
|----------|-------|
| **Tag name** | `governance/greenfield-quality-passed` |
| **Subject** | `greenfield-quality-passed` |
| **Decision state** | `passed` |
| **Target commit** | `55f234afbb328c24c15400a745fdc99c7ceafae9` |
| **Tag type** | `annotated` |
| **Signature** | NOT REQUIRED |

### 13.2 Authorized Message

```
MEDIA CAPABILITY PLATFORM
GREENFIELD QUALITY BASELINE PASSED

Candidate: 55f234afbb328c24c15400a745fdc99c7ceafae9
Quality task: ARCH-CODE-GOV-GREENFIELD-BASELINE-GRADLE-QUALITY-TASKS.2B-RE-RUN.1
Quality result: GRADLE_QUALITY_TASKS_2B_RE_RUN_PASSED
Tests: 5730
Failures: 0
Errors: 0
Skipped: 41
Unexpected new skips: 0
Unexplained skips: 0
Schema categories: 22/22
DG-001: PASS
Architecture invariants: 12/12
JaCoCo tasks: 64/64
Credential exception: ARCH-CODE-GOV-EXCEPTION-INJECTION-4-PAT-EXPOSURE.1
Credential residual risk: PRESENT
Remote publication: NOT AUTHORIZED
```

### 13.3 Decision Meaning

This tag signifies:

> The greenfield quality baseline (Quality Tasks.2B) has passed and received governance acceptance. The candidate commit `55f234af...` has met all quality gates including test counts, schema validation, architecture invariants, and JaCoCo coverage.

### 13.4 Quality Metrics (Binding)

| Metric | Value |
|--------|-------|
| Tests | 5730 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 41 |
| Unexpected new skips | 0 |
| Unexplained skips | 0 |
| Schema categories | 22/22 |
| DG-001 | PASS |
| Architecture invariants | 12/12 |
| JaCoCo tasks | 64/64 |
| JaCoCo failures | 0 |

### 13.5 Target Commit Relationship

The authorized quality-passing tag target is `55f234afbb328c24c15400a745fdc99c7ceafae9`.

This contract commit is a subsequent governance control-plane document and does NOT alter the already-verified quality payload.

This contract's creation:

- MUST NOT reopen quality verification
- MUST NOT alter quality task result
- MUST NOT require re-running quality tasks
- MUST NOT be used as a tag target

### 13.6 Credential Exception Gate

| Property | Value |
|----------|-------|
| **Exception ID** | `ARCH-CODE-GOV-EXCEPTION-INJECTION-4-PAT-EXPOSURE.1` |
| **Status** | `ACTIVE_TEMPORARY_EXCEPTION` |
| **Review/expiry date** | `2026-08-04` |
| **Token rotated** | NOT ROTATED |
| **Residual risk** | PRESENT |

If Tag Finalization execution time is **earlier than** 2026-08-04:
- Local tag creation MAY proceed after other governance conditions are met.

If Tag Finalization execution time **reaches or exceeds** 2026-08-04:
- AND no new risk review acceptance record exists
- Tag Finalization MUST stop.

The credential exception:
- DOES NOT authorize remote push
- DOES NOT represent that credential leakage is fully resolved
- Is a time-limited governance exception only

### 13.7 Independence Requirements

The following are independent governance operations:

1. **Contract acceptance** — independent verification that this contract is correct
2. **Tag finalization** — independent execution of the tag creation authorized by this contract
3. **Tag finalization acceptance** — independent verification that the created tag is correct
4. **Remote publication** — separate authorization not covered by this contract

Each requires its own governance task.

### 13.8 Conflict Handling

If `governance/greenfield-quality-passed` already exists:
- MUST NOT overwrite
- MUST NOT delete
- MUST NOT force-create
- MUST stop and report conflict

Only after confirming the tag does not exist locally and remotely (via credential-free, secure authentication) may Tag Finalization proceed.

### 13.9 Contract Authorization Boundary

This contract authorizes ONLY:
- Creation of one new local annotated tag
- Tag name: `governance/greenfield-quality-passed`
- Tag target: `55f234afbb328c24c15400a745fdc99c7ceafae9`
- Tag message: exact message in §13.2
- Signature: not required
- Post-creation: independent Tag Finalization Acceptance Reverification

This contract DOES NOT authorize:
- Moving existing tags
- Overwriting same-name tags
- Deleting any tags
- Creating lightweight tags
- Signing releases
- Creating GitHub releases
- Pushing branches
- Pushing tags
- Remote publication
- Modifying the candidate commit
- Modifying Git history

---

## 14. Quality Tag Finalization Record

### 14.1 Finalization Status

| Property | Value |
|----------|-------|
| **Tag name** | `governance/greenfield-quality-passed` |
| **Tag object** | `69ee67a40900217db8245a216a7a95f3f4678f72` |
| **Tag target** | `55f234afbb328c24c15400a745fdc99c7ceafae9` |
| **Tag type** | `annotated` |
| **Signature** | ABSENT |
| **Tag message SHA-256** | `b457b3e8e126931a9e67261d1238b5ad5b4500a55dbc44ce4bb89350d1563302` |

### 14.2 Finalization Acceptance Chain

| Step | Status |
|------|--------|
| Tag Finalization independent acceptance | `QUALITY_PASSING_LOCAL_TAG_FINALIZATION_ACCEPTANCE_REVERIFIED` |
| Tag Finalization governance acceptance | `QUALITY_PASSING_LOCAL_TAG_FINALIZATION_GOVERNANCE_ACCEPTED` |
| Tag object fixed | YES |
| Tag target fixed | YES |
| Quality Tag immutable | YES |

### 14.3 Quality Baseline Closure

The Greenfield Quality Baseline is now:

```
GREENFIELD_QUALITY_BASELINE_CLOSED_LOCALLY
```

This closure signifies:

1. Quality implementation commit `55f234af...` has been governance-accepted.
2. Full Gradle Quality Tasks.2B has passed and been governance-accepted.
3. Local annotated Quality Tag has been created.
4. Quality Tag has completed independent reverification.
5. Quality Tag Finalization has received governance acceptance.
6. Local quality baseline can serve as input for subsequent Mainline Readiness Governance.

### 14.4 Closure Does NOT Authorize

This closure:

- **DOES NOT** authorize remote tag publication
- **DOES NOT** authorize GitHub Release creation
- **DOES NOT** close the Credential Risk Exception
- **DOES NOT** represent that credential exposure is fully resolved
- **DOES NOT** declare the project Mainline Ready
- **DOES NOT** authorize Mainline Readiness work to begin without independent governance
- **DOES NOT** authorize OpenCue implementation
- **DOES NOT** authorize Artifact DAG work
- **DOES NOT** authorize Frontend restoration

### 14.5 Credential Exception Continuity

| Property | Value |
|----------|-------|
| **Exception ID** | `ARCH-CODE-GOV-EXCEPTION-INJECTION-4-PAT-EXPOSURE.1` |
| **Status** | `ACTIVE_TEMPORARY_EXCEPTION` |
| **Start date** | 2026-07-21 |
| **Review/expiry date** | 2026-08-04 |
| **Token rotated** | NOT ROTATED |
| **Residual risk** | PRESENT |

The Quality Baseline Closure does NOT close the Credential Risk Exception.

The Quality Baseline Closure does NOT represent that the exposure is fully resolved.

The risk exception must still be reviewed or the token rotated before 2026-08-04.

The risk exception does NOT authorize Remote Push.

The risk exception does NOT authorize GitHub Release.

Token type: UNKNOWN. Repository scope: UNKNOWN. Permissions: UNKNOWN. Expiration: UNKNOWN. Production access: UNKNOWN (owner asserted NO, not independently verified). Organization administration: UNKNOWN (owner asserted NONE, not independently verified).

Do NOT infer actual token permissions from Remote URL.

### 14.6 Remote Publication Boundary

| Property | Value |
|----------|-------|
| Local quality Tag | FINALIZED |
| Remote quality Tag | NOT PUBLISHED |
| Remote publication | NOT AUTHORIZED |
| GitHub Release | NOT CREATED |
| Branch Push | NOT AUTHORIZED |

Future Remote Publication must go through an independent task and independent authorization.

Closure cannot imply remote publication permission.

### 14.7 Mainline Readiness Boundary

Closure only allows subsequent entry to:

```
MAINLINE READINESS GOVERNANCE
```

Closure must NOT be written as Mainline Ready.

Post-closure steps still required:

1. jOOQ hard-coding/codegen inventory
2. stale/compatibility code inventory
3. debt authority and retirement decisions
4. BLOCKER remediation
5. Mainline Readiness independent verification
6. OpenCue mainline implementation authorization

Debt classification categories:

```
BLOCKER
REMOVE_BEFORE_MAINLINE
DEPRECATE_WITH_DEADLINE
QUARANTINE
KEEP
```

### 14.8 Immutability

The Quality Tag `governance/greenfield-quality-passed` with object `69ee67a40900217db8245a216a7a95f3f4678f72` pointing to target `55f234afbb328c24c15400a745fdc99c7ceafae9` is now **immutable**.

- MUST NOT be moved
- MUST NOT be deleted
- MUST NOT be overwritten
- MUST NOT be recreated
- MUST NOT have its peeled commit changed
- MUST NOT have its annotated message modified

The existing baseline Tag `governance/greenfield-baseline-accepted` with object `36b56168623abdcdff56d5da1cff7a23baf4741d` pointing to target `673e180a3236d747b4fd2aaaa5ca7a11a0cf830d` remains unchanged and independent.

The two tags represent different governance milestones and must NOT be deleted, overwritten, moved, replaced, or have their semantics merged.
