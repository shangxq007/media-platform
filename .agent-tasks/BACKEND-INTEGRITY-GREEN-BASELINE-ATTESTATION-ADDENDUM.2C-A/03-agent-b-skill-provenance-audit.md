# Agent B: Kanban Skill Provenance Audit

## Known Hashes

```
2C starting hash: 54827b331dc99fcaa3aa0dae16c1256a41e0c4547b7e8e1fc1b9c0178db10853
2C observed changed hash: 7705362f4daa052cbf8c508be1f4d6cd9a9df289831832ca6c4c14c75dc1bc1c
```

## File Metadata

```
Modify: 2026-07-16 10:44:34.570172823 +0800
Change: 2026-07-16 10:44:34.570172823 +0800
Birth:  2026-07-16 10:44:34.569172818 +0800
```

Birth = Modify means the file was CREATED (not incrementally modified) at 10:44:34.

## Timeline

```
~03:34 — CLOSEOUT.2B restored Skill (274 lines, hash 54827b33...)
~10:44 — File recreated by external process (375 lines, hash 7705362f...)
```

## Investigation

### Hermes Agent Logs

```
agent.log shows curator attempted to patch kanban skill multiple times:
- 2026-07-15 16:37: "Refusing background curator patch" (safety check)
- 2026-07-15 23:34: "Refusing background curator patch" (safety check)
- 2026-07-15 23:35: "Refusing background curator write_file" (safety check)
- 2026-07-16 09:38: "Refusing background curator patch" (safety check)
```

All curator patches were REFUSED by the safety check ("current SKILL.md content has not been loaded in this review turn").

### Curator Logs

```
~/.hermes/logs/curator/ — last run: 2026-07-15 02:34
No curator run on 2026-07-16
```

### Process Audit

No explicit tool call or process was found that created the file at 10:44:34. The agent.log shows no skill_manage operations at that time. The curator didn't run on Jul 16.

### Content Check

The recreated file (375 lines) does NOT contain the unauthorized additions (Agent D may not commit, Test Baseline Recovery, FFmpeg Test Environment). But it has 101 more lines than the 2C restoration.

## Classification

```
UNPROVEN_EXTERNAL_CHANGE
```

The change source cannot be proven. The file was recreated by an unidentified process.

## Disposition

```
RESTORED_TO_2C_STARTING_HASH
```

The Lead restored the exact 2C starting content via skill_manage(action='edit').

## Restoration Verification

```
Final hash: 54827b331dc99fcaa3aa0dae16c1256a41e0c4547b7e8e1fc1b9c0178db10853
Matches 2C starting hash: YES
```
