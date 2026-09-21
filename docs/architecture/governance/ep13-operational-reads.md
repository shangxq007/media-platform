# EP13 operational reads

Outbox owns its status-count snapshot; Render owns render-job and client-export-session status projections. These are internal read-only diagnostics, not business lifecycle decisions. Queries propagate persistence failure. No generic SQL or repository is exported.

HealthController composes owner queries. Global `/metrics/summary` requires Identity's verified `identity.platformAdministrator` fact; tenant ADMIN is insufficient. This closes a prior anonymous global-count exposure. The authentication filters populate this fact; headers are not accepted as authority. Public readiness reports availability only, without global pending counts or database exception text. Outbox query failure now degrades readiness instead of reporting skipped with an overall healthy status. Empty owner results remain successful empty counts.

Storage/database checks retain their existing connection semantics; this change does not establish real storage-provider readiness. No schema, frontend field addition, business write or duplicate operational registry is introduced.
