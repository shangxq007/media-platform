# Governance Debt Register

## Frozen Architecture Rules

| Rule | Status | Source |
|------|--------|--------|
| Product = canonical business object | FROZEN_ACCEPTED | ADR |
| TimelineRevision = immutable canonical snapshot | FROZEN_ACCEPTED | ADR |
| RenderJob = one immutable execution attempt | FROZEN_ACCEPTED | ADR |
| Retry creates a new RenderJob | FROZEN_ACCEPTED | ADR |
| FALLBACKING excluded | FROZEN_ACCEPTED | ADR |
| RETRYING excluded | FROZEN_ACCEPTED | ADR |
| OpenCue = ExecutionEnvironment | FROZEN_ACCEPTED | ADR |
| FFmpeg/Remotion/GPAC/Blender = ExecutionBackend | FROZEN_ACCEPTED | ADR |
| Artifact DAG postponed indefinitely | FROZEN_ACCEPTED | ADR |
| Timeline Git > Artifact DAG cache | FROZEN_ACCEPTED | ADR |
| V1-V4 frozen | FROZEN_ACCEPTED | migration |
| V5 blocked pending governance | FROZEN_ACCEPTED | 2C-A |
| Frontend paused until backend stable | FROZEN_ACCEPTED | 2C-A |

## Governance Debts

| ID | Description | Severity | Resolution Phase |
|----|-------------|----------|-----------------|
| DEBT-01 | Root receipt missing at /var/lib/hermes/receipts/ | MEDIUM | .6A |
| DEBT-02 | umount failure may be hidden by fault-tolerant semantics | LOW | .6A |
| DEBT-03 | Same UID can start additional gateway process | MEDIUM | .6A |
| DEBT-04 | Persistence verified by controlled service cycle, not host reboot | LOW | .6A |
| DEBT-05 | Native delegate tool restriction unavailable | MEDIUM | .6A |
| DEBT-06 | Post-task hooks historically modified Skills | HIGH | .6A |

## Known Conflicts Summary

- 21 conflicts identified by Agent C
- 3 HIGH, 10 MEDIUM, 8 LOW
- See conflict-register.json for full details
