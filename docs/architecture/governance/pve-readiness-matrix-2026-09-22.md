---
metadata_schema_version: 1
document_id: "architecture-governance-pve-readiness-matrix-2026-09-22"
title: "PVE and External Runtime Readiness Matrix"
artifact_type: "DEPLOYMENT_DOCUMENT"
domain: "architecture-governance"
authority_class: "EVIDENCE_ONLY"
lifecycle_state: "ACTIVE"
acceptance_state: "NOT_APPLICABLE"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
retention_class: "LONG_TERM"
generated: false
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# PVE and external runtime readiness matrix

This is a readiness checklist, not a deployment approval. No PVE, production,
OIDC, or external-provider call was made during this acceptance run.

| Requirement | Status | Evidence or missing prerequisite |
|---|---|---|
| API application | AVAILABLE locally | Backend build, API contract gate, and accepted runtime evidence pass |
| Frontend | AVAILABLE locally | npm lockfile restored; lint, typecheck, focused tests, full suite, and build pass |
| PostgreSQL | ENVIRONMENT-DEPENDENT | Compose and Flyway definitions exist; Docker runtime is unavailable locally |
| Temporal Server | ENVIRONMENT-DEPENDENT | Required for `render.execution.mode=temporal`; server, namespace, persistence, and network endpoint must be supplied |
| Temporal Workers | ENVIRONMENT-DEPENDENT | Worker modules and deployment descriptions exist; running worker fleet not verified |
| Storage | ENVIRONMENT-DEPENDENT | S3/local storage adapters exist; production bucket, credentials, retention, and egress are not supplied |
| Render Worker | AVAILABLE AS ARTIFACT / NOT RUNTIME-VERIFIED | Dockerfile and module definitions exist; PVE execution not run |
| Sandbox Worker | AVAILABLE AS ARTIFACT / NOT RUNTIME-VERIFIED | Isolation module and deployment relationships exist; host containment not verified |
| OIDC | ENVIRONMENT-DEPENDENT | Frontend OIDC transport is tested locally; issuer, client, redirect URI, keys, and production claims are absent |
| Reverse proxy and TLS | MISSING ENVIRONMENT EVIDENCE | Ingress/reverse-proxy topology is documented; certificate, hostname, and TLS probes require PVE |
| Configuration and secrets | MISSING ENVIRONMENT EVIDENCE | Secret injection is documented; real values must come from the target secret manager |
| Registry and image provenance | ENVIRONMENT-DEPENDENT | Image build definitions exist; registry access, digests, signing, and promotion evidence are absent |
| Observability | ENVIRONMENT-DEPENDENT | Health/metrics/Sentry relationships are documented; live collectors and alert routes are unverified |
| Backup and restore | DEFERRED EXTERNAL CHECK | PostgreSQL backup policy exists; restore drill requires target infrastructure and data handling approval |
| Network and trust boundaries | DEFERRED EXTERNAL CHECK | Logical trust boundaries are documented; firewall, DNS, egress, and provider allowlists require PVE |

PVE readiness remains conditional until the environment-specific rows have
fresh evidence. Local tests must not be interpreted as PVE or production
acceptance.
