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
