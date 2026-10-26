# Current project handoff

The canonical development repository is the Owner-adopted mainline, not a historical Candidate or G2
worktree. Follow [development instructions](../development/canonical-baseline.md), the
[engineering principles](../engineering/functional-development-and-module-principles.md), and the
[execution boundaries](../architecture/current/execution-architecture-principles.md).

The single V1 baseline, actual PostgreSQL18 migration, jOOQ generation, independent API/Worker,
First Render terminal state and authorized HTTP download were accepted in the mainline consolidation.
Historical G1/G2/C-2 reports and old V5 instructions are source history, not current deployment steps.
Do not recreate deleted migration series, reinstate obsolete role guards, or apply a baseline to an
old populated database. Current database policy is [the single Flyway baseline](../database/flyway-migration-baseline.md).

Legacy-work dispositions and their concrete dependencies belong in
[the current consolidation record](../development/legacy-work-consolidation.md); old reports are
retained in Git and protected task evidence. Prior handoff text remains at mainline commit509e6aca;
it is not copied forward as a second source of current instructions.

Real protected Runner/OS identities, Neon/BWS privileges and production deployment remain separate
admission gates: `DEPLOYMENT_ISOLATION_NOT_VERIFIED`. The local functional acceptance does not certify
those gates. Timeline/Operation feature work requires its own authorized task.
