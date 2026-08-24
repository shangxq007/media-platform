# Project State — Repository Canonical Project Memory

Status: MUTABLE GOVERNANCE INDEX SET (v1, bootstrap revision 2)
Location: `docs/architecture/governance/project-state/`
Persisted by: PROJECT_ARCHITECTURE_STATE_V1 CONSOLIDATED GOVERNANCE BOOTSTRAP REVISION 2

## 1. Why project-state exists

The repository is the durable project-memory authority. Long-running
conversation memory is a convenience cache, not project authority. A future
Hermes/ChatGPT session must be able to read the repository and answer "where
are we, what is closed, what is next, which architecture is adopted, which
technologies are adopted vs POC vs reference, what was consciously deferred,
which validation projects prove the platform" without depending on
conversation history.

This directory is the machine-navigable entry point for that recovery.

## 2. Canonical memory principles (V1)

- REPOSITORY_IS_CANONICAL_PROJECT_MEMORY_AUTHORITY_V1
- CONVERSATION_MEMORY_IS_A_CONVENIENCE_CACHE_NOT_PROJECT_AUTHORITY_V1
- DISCUSSION_ADOPTION_IS_DISTINCT_FROM_REPOSITORY_PERSISTENCE_V1
- CURRENT_STATE_INDEX_IS_MUTABLE_BUT_HISTORY_IS_GIT_VERSIONED_V1
- ARCHITECTURE_DECISIONS_HAVE_STABLE_IDS_V1
- ROADMAP_STATUS_MUST_REFERENCE_CONCRETE_REPOSITORY_EVIDENCE_V1
- DEFERRED_WORK_MUST_BE_EXPLICITLY_INDEXED_NOT_LEFT_TO_MEMORY_V1
- VALIDATION_PROJECTS_ARE_FIRST_CLASS_GOVERNANCE_ARTIFACTS_V1
- TECHNOLOGY_CANDIDATE_IS_DISTINCT_FROM_ADOPTED_ARCHITECTURE_DEPENDENCY_V1
- REPOSITORY_STATE_MUST_DISTINGUISH_ADOPTED_PERSISTED_DEFERRED_AND_POC_V1

Conversation memory exists and is useful as temporary working context; it is
simply not the durable authority. The repository is.

## 3. Files in this directory

Mutable indexes (update in place; history is Git-versioned):

| File | Content |
|---|---|
| current-state.yaml | Compact current project snapshot: SHAs, roadmap status, next step |
| architecture-registry.yaml | Stable-ID index of adopted architecture principles (9A-9AC) |
| roadmap-tracks.yaml | Cross-cutting tracks (not milestone renumbering) |
| foundation-inventory.yaml | Technology/foundation choices with one primary status each |
| product-reference-inventory.yaml | Product/competitor references (REFERENCE, never dependency) |
| deferred-items.yaml | Consciously deferred work (must never silently disappear) |
| validation-inventory.yaml | First-class validation projects with metrics and triggers |

Immutable historical evidence (do NOT edit; correct via new governance record):

- Accepted milestone publications and final-review records under
  `docs/architecture/governance/` (e.g. `roadmap-21-execution-graph-planning-final-review-and-integration.md`,
  `roadmap-22-*-amendment-*.md`, `roadmap-22-epoch-3-*.md`, `first-real-media-cut-v1.md`)
- Frozen decision-recovery contracts (e.g. `roadmap-22-executable-task-worker-fabric-decision-recovery.md`)
- Canonical contracts under `docs/architecture/governance/canonical-contracts/`
- ADRs under `docs/architecture/adr/`
- The consolidation record `docs/architecture/governance/project-architecture-state-v1-consolidated-governance-bootstrap-revision-2.md`

## 4. Recovery workflow (new session)

1. READ `current-state.yaml` — where are we, what is next
2. READ `roadmap-tracks.yaml` — active tracks and their next actions
3. READ relevant IDs in `architecture-registry.yaml`
4. READ `deferred-items.yaml` — what was consciously deferred
5. READ `validation-inventory.yaml` — which validation projects prove the platform
6. FOLLOW linked immutable decisions/evidence (decision recoveries, amendments,
   canonical contracts, ADRs, acceptance records)
7. CONTINUE WORK

Do not re-derive architecture from conversation memory. The repository is the
authority.

## 5. Authority precedence

Repository-wide precedence is defined once in
`docs/architecture/governance/source-of-truth/authority-precedence.md`
(L0 user approval ... L7 historical record). In short, for this directory:

1. Frozen canonical contracts / frozen architecture decisions (L1-L2)
2. Accepted milestone publication / final review records (L1)
3. project-state architecture registry / roadmap indexes (this directory, L5)
4. current-state projection (this directory, L5)
5. conversation working context (no authority)

If a mutable index contradicts a frozen contract, the frozen contract wins
until the index is corrected by a new governance update.

## 6. Update rules

- Update current-state.yaml on every adopted milestone/epoch/phase/closure
  change, with concrete repository evidence (SHA/tree or doc path) in the same
  update.
- Add architecture-registry entries when a discussion is ADOPTED; never reuse
  an existing ID for a different principle (stable IDs).
- One primary status per foundation entry; statuses are
  ADOPTED / PLANNED / POC_CANDIDATE / REFERENCE_ONLY / DEFERRED.
- Deferred items: never delete silently — mark DEFERRED or RESOLVED with
  evidence.
- Full 40-character SHAs, deterministic ordering, 2-space YAML indentation,
  no anchors, no placeholders, no implicit magic dates.
- Do NOT mark things implemented/integrated without repository evidence
  (no false claims — see section 19 of the bootstrap prompt).

## 7. Status vocabulary

ADOPTED — architecture decision accepted (may be unimplemented)
PERSISTED — adopted decision now recorded in repository governance
PLANNED — scheduled future work
DEFERRED — consciously postponed; revisit trigger recorded
POC_CANDIDATE — evaluation/proof-of-concept candidate
REFERENCE_ONLY — study/reference; no architecture dependency
IMPLEMENTED / PARTIALLY_IMPLEMENTED — repository evidence exists
FROZEN — immutable contract
CLOSED — milestone/epoch accepted and closed
REJECTED — considered and rejected

## 8. Recording a new adopted discussion

1. Add stable-ID entry/entries to `architecture-registry.yaml`
   (status: ADOPTED, persistence: REPOSITORY_PERSISTED).
2. If a technology is involved, set its foundation-inventory status.
3. If work is consciously postponed, add a deferred-items entry.
4. If it is a validation project, add a validation-inventory entry.
5. Update `current-state.yaml` (and roadmap-tracks if a track changes).
6. Commit docs-only on the active governed branch; for major adoptions also
   publish an immutable adoption record under `docs/architecture/governance/`.
7. Chat/GPT governance review before milestone closure, per established chain.
