# Skill Recovery Source Register

## Searched Sources

| Source | Found | Content | Hash Match |
|--------|-------|---------|------------|
| Forensics snapshots | YES | Current live copies | NO (current, not historical) |
| Filesystem backups | NO | — | — |
| Editor backups | NO | — | — |
| Temporary files | NO | — | — |
| Shell history | N/A | — | — |
| Hermes task artifacts | NO | — | — |
| .agent-tasks evidence | YES | Method description only | N/A (no content bytes) |
| Tool-call logs | NOT FOUND | — | — |
| Conversation exports | NOT FOUND | — | — |
| Skill exports | NO | — | — |
| Curator logs | YES | "no changes" | N/A |
| Plugin caches | NOT CHECKED | — | — |
| Sync caches | NO | — | — |
| Package caches | NO | — | — |
| Home backups | NO | — | — |
| PVE snapshots | NOT AVAILABLE | — | — |
| Git history | NO (skills dir not tracked) | — | — |
| Hermes checkpoints | NO (empty) | — | — |
| agent.log tool calls | NOT FOUND | — | — |

## Result

```
HISTORICAL_EXACT_BYTES_UNRECOVERABLE
```

No source contains the exact bytes that produce the expected hashes.

## Candidate Status

```
NOT_CREATED
```

No new baseline candidates generated. Requires user decision on:
1. Accept current content as new baseline
2. Provide external backup source
3. Authorize Hermes to generate candidates from current content
