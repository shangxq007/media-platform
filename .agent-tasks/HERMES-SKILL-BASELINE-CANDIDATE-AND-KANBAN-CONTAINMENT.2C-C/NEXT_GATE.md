# Next Gate

## Current State

2C-C candidates generated. User approval required.

## Required Before Write-Back

1. User explicitly approves both candidate hashes
2. Curator remains paused during write-back
3. Write-back performed by sole candidate preparer
4. Stability verified after write-back
5. 2C-A E1/E2 executed on clean evidence commit

## User Approval Packet

Location: ~/.hermes/forensics/media-platform-2c-c-20260716142608/candidate/CANDIDATE_APPROVAL_PACKET.md

## Required User Response

```
APPROVE: Accept both candidates as new canonical baseline
REJECT: Do not accept, require further investigation
REQUIRE_EDITS: Specify what changes are needed
```

## After Approval

Next task: USER-APPROVAL-SKILL-BASELINE-REBASE.2C-D
- Write approved candidates to live Skill paths
- Verify stability
- Run 2C-A E1/E2

## NOT Allowed

- Starting ARCH-DOC-GOV
- Continuing V5
- Restarting curator
- Auto-approving candidates
