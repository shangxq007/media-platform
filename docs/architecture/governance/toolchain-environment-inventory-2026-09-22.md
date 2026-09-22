---
metadata_schema_version: 1
document_id: "architecture-governance-toolchain-environment-inventory-2026-09-22"
title: "Toolchain and Environment Inventory"
artifact_type: "TEST_OR_GUARD"
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

# Toolchain and environment inventory

| Area | Toolchain | Fresh result | Environment note |
|---|---|---|---|
| Backend | Gradle wrapper, Java 25 toolchain | PASS in prior accepted evidence | Reused generated-C4 and Modularity evidence; no source changes since that run |
| Documentation | Python 3.13.15 | PASS, 16/16 | Fresh on final repository state |
| LikeC4 | `likec4@1.58.0` via `npx --yes` | PASS | Fresh |
| Architecture drift | Python guard | PASS, 50 modules / 3 deployment units | Fresh |
| HTTP contract | Spectral 6.14.3, oasdiff 1.28.0 | PASS, 5/5 gate checks | Fresh; oasdiff restored in ignored `scripts/tools/` path |
| Semgrep | `uvx semgrep==1.175.0` | PASS, matrix and full scan | Fresh; `uv`/Semgrep used in `/tmp/media-platform-semgrep-venv`, with `SSL_CERT_FILE=/etc/ssl/ca-bundle.pem` |
| Frontend dependencies | npm 11.19.0, Node 26.7.0, `npm ci` | PASS, 636 packages | Fresh from `frontend/package-lock.json`; `frontend/node_modules` is ignored |
| Frontend build | Vite 6.4.3 | PASS | Fresh; generated static output was restored after identity capture |
| Docker Compose | Docker executable | NOT_RUN | `docker` is unavailable; static Compose/Flyway path validation passed |
| External providers and PVE | Provider credentials and authorized environment | NOT_RUN | Deliberately outside local acceptance scope |

No global package manager state was modified. Temporary Semgrep dependencies
were installed outside the repository and are not acceptance artifacts.
