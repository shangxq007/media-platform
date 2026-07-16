# Timeline

## Key Events (chronological)

| Timestamp | Event | Actor | Impact |
|-----------|-------|-------|--------|
| 2026-07-16 ~03:34 | CLOSEOUT.2B restored Skills | Lead | Skills set to expected hashes |
| 2026-07-16 ~10:44 | kanban Skill recreated by external process | Unknown | Hash changed to 7705362f... |
| 2026-07-16 ~10:46 | java-test-repair modified by external process | Unknown | Hash changed |
| 2026-07-16 10:52 | Kanban tasks t_82581ccd and t_5befaae7 created | Kanban system | Tasks created as blocked |
| 2026-07-16 10:53 | Both tasks auto-promoted and claimed | Kanban gateway | Automated execution started |
| 2026-07-16 10:53-10:59 | t_82581ccd runs 332 seconds (Run #22) | Automated | Document inventory produced |
| 2026-07-16 10:53-11:09 | t_5befaae7 runs (Run #23) | Automated | V5 code committed (60d4ac5) |
| 2026-07-16 ~11:00 | Kanban tasks marked done | Automated lifecycle | False completion signals |
| 2026-07-16 ~13:20 | FOLLOWUP task attempts Skill restoration | Lead | Hashes still mismatched |
| 2026-07-16 14:06 | 2C-B forensic task starts | Lead | Forensics snapshots taken |
| 2026-07-16 14:06 | Curator paused | Lead | External mutation frozen |

## Root Cause Analysis

The Kanban tasks were created by `hermes kanban create` during the 2C-A task. The Kanban gateway automatically promoted them from blocked → ready → claimed → done. This happened because:

1. Tasks were created with `--initial-status blocked`
2. The gateway auto-promotes blocked tasks when the assignee is available
3. The gateway spawned automated agents that executed the tasks
4. The tasks ran to completion before 2C-A was accepted

This was a gate-violation: both tasks should have remained blocked until 2C-A was independently accepted.

## Skill Modification Source

The curator is the most likely source, despite showing "no changes" in its last run. The curator may have:
1. Modified Skills during an earlier run (Jul 15)
2. Had its changes overwritten by the 2C-A restoration
3. Then the Skills were re-modified by another process

The exact source remains UNRESOLVED. The curator is now paused.
