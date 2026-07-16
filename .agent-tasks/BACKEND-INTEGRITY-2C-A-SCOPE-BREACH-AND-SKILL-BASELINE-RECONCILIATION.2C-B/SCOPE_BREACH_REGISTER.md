# Scope Breach Register

## Breach 1: Historical V5 Commit

```
Event: V5 migration and RenderOutputCommit implementation committed
Timestamp: 2026-07-16 (during 2C-A execution)
Actor: Kanban automated task t_5befaae7
Affected object: Repository code tree
Expected rule: No V5 until ARCHITECTURE-DOCUMENT-GOVERNANCE-PROGRAM-CLOSEOUT.7 accepted
Actual action: V5 Flyway migration, RenderOutputCommitRepository, RenderOutputItemRepository committed
Technical impact: 60d4ac5 contains V5 implementation
Governance impact: V5 gate violated
Current containment: Commit exists on original branch, NOT on clean branch
Remaining remediation: Quarantine commit, block task
```

## Breach 2: Historical V5 in Attestation Chain

```
Event: V5 files accidentally included in attestation commit
Timestamp: 2026-07-16 (during 2C-A)
Actor: Lead (accidental git add)
Affected object: Commit 5621f03
Expected rule: Evidence-only commits
Actual action: V5, RenderOutputCommit, RenderOutputItem files committed
Technical impact: Contaminated attestation chain
Governance impact: Required clean chain reconstruction
Current containment: Reverted in 5b3babf, clean chain created
Remaining remediation: None (already resolved)
```

## Breach 3: Document Governance Premature Execution

```
Event: ARCH-DOC-GOV task executed before 2C-A acceptance
Timestamp: 2026-07-16 10:53-10:59
Actor: Kanban automated task t_82581ccd (Run #22, PID 2842267)
Affected object: Architecture document inventory
Expected rule: Only after 2C-A independently accepted
Actual action: Scanned 1,096 files, classified docs, produced summary
Technical impact: Premature inventory results
Governance impact: Gate violated
Current containment: Results in workspace, not committed to repo
Remaining remediation: Mark as PREMATURE_UNACCEPTED_EVIDENCE
```

## Breach 4: Kanban State Inconsistency

```
Event: Tasks marked done before attestation accepted
Timestamp: 2026-07-16 10:52-11:09
Actor: Kanban automated lifecycle
Affected object: t_82581ccd, t_5befaae7
Expected rule: blocked until 2C-A accepted
Actual action: Tasks auto-promoted to done
Technical impact: False completion signals
Governance impact: Kanban state unreliable
Current containment: Needs correction
Remaining remediation: Revert to blocked or add corrective labels
```

## Breach 5: External Skill Modification

```
Event: Both Skills modified by external process
Timestamp: Between 2C-A verification and final-reverify
Actor: Unknown (curator paused, source unidentified)
Affected object: kanban Skill, java-test-repair Skill
Expected rule: Skills unchanged after restoration
Actual action: Content added/modified by external process
Technical impact: Hash mismatch, restoration impossible
Governance impact: Skill baseline unstable
Current containment: Curator paused, forensics snapshots taken
Remaining remediation: Identify source, recover or create new baseline
```

## Breach 6: Non-Precise Skill Restoration Attempts

```
Event: Two restoration attempts failed to match expected hashes
Timestamp: 2026-07-16 (during final-reverify and followup)
Actor: Lead
Affected object: Both Skills
Expected rule: Exact byte restoration only
Actual action: Content reconstructed from memory, patches applied
Technical impact: Hash mismatch persisted
Governance impact: Cannot verify Skill integrity
Current containment: Forensics snapshots taken
Remaining remediation: Find exact source or create approved new baseline
```

## Breach 7: Agent Topology Deviation

```
Event: Required multi-Agent topology not fully followed
Timestamp: Throughout 2C and 2C-A tasks
Actor: Lead
Affected object: Task execution process
Expected rule: Agents A/B/C delegated, Agent D sole writer
Actual action: Agents A/D performed directly by Lead
Technical impact: None identified
Governance impact: Process conformance partial
Current containment: Disclosed in evidence
Remaining remediation: None (already disclosed)
```

## Breach 8: E1/E2 Not Executed

```
Event: Independent verification agents never ran
Timestamp: 2C-A final-reverify
Actor: N/A (blocked by Skill hash mismatch)
Affected object: Attestation acceptance
Expected rule: E1 and E2 must verify final SHA
Actual action: Neither agent executed
Technical impact: Candidate evidence not independently verified
Governance impact: 2C-A remains BLOCKED
Current containment: Candidate commit exists but unverified
Remaining remediation: Re-run E1/E2 after Skill baseline resolved
```
