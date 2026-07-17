# Non-Authoritative Sources Register

## Objects That Must NEVER Be Authority Sources

| Object | Correct Role | Why Not Authority |
|--------|-------------|-------------------|
| .agent-tasks/** | EVIDENCE_ONLY (L6) | Task evidence, not canonical |
| Memory entries | CONTEXT_AND_AUDIT_ONLY | NOT_AN_APPROVAL_SOURCE |
| Holographic memory | CONTEXT_ONLY | NOT_AN_APPROVAL_SOURCE |
| Kanban done status | System state (L4) | done ≠ accepted, done ≠ verified |
| /tmp detached receipts | EPHMERAL_EVIDENCE (L6) | Cannot define architecture |
| Forensics snapshots | FORENSIC_ONLY (L6) | Incident evidence |
| Runtime logs | Behavior evidence (L6) | Not design authority |
| Quarantined V5 commit 60d4ac5 | QUARANTINED (L7) | NOT_ACCEPTED, NOT_AUTHORITY |
| Superseded ADRs | HISTORICAL (L7) | Superseded |
| Stale target-state docs | HISTORICAL (L7) | Postponed/obsolete |
| Implementation contradicting frozen contract | DRIFT | Contract wins over implementation |
| README.md | SUPPORTING (L5) | Entry point, not canonical |
| AGENTS.md | SUPPORTING (L5) | Agent instructions, not architecture |

## Rules

1. **Evidence proves events, not design** — .agent-tasks can show what happened but cannot define what should be
2. **Memory is context, not authority** — Memory entries provide audit trail but cannot approve or define architecture
3. **Kanban state is system state, not acceptance** — `done` means the Kanban task completed, not that the work was accepted
4. **Runtime is observation, not design** — L4 shows what IS, not what SHOULD BE
5. **History is reference, not current** — L7 documents what was, not what is authoritative now
