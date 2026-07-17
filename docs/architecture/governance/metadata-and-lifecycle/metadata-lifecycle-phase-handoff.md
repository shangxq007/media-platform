# Metadata Lifecycle Phase Handoff

## .5 → .6 Handoff

### Guard Requirements
1. Metadata schema validation (document-metadata.schema.json)
2. Document ID uniqueness check
3. Lifecycle transition validation
4. Canonical normative body protection
5. Accepted ADR decision-body protection
6. Broken-link detection (126 pre-existing)
7. Receipt exact-worktree validation
8. Reviewer/verifier ordering
9. V5 quarantine guard
10. render-output candidate guard
11. .agent-tasks non-authority guard

### Inputs Delivered
- document-metadata.schema.json
- document-metadata-registry.json
- lifecycle-transition-matrix.json
- document-ownership-registry.json
- review-cadence-policy.json
- governance-receipt-registry.json
- metadata-exceptions-register.json

## .5 → .6A Handoff

### Control-Plane Guard Requirements
1. Root receipt lifecycle implementation
2. umount error reporting
3. Same-UID risk documentation
4. Host reboot verification
5. Delegate tool restriction documentation

## .5 → .7 Handoff

### Closeout Requirements
1. All guards passing
2. Root receipt created and verified
3. All debts resolved or accepted
4. V5 unblock conditions met
