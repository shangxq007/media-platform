# Experimental Infrastructure Platform Foundation

**Date:** 2026-07-02
**Status:** PLANNED
**Authority:** PLATFORM-FOUNDATION.0

---

## Platform Mission

General-purpose experimental infrastructure platform for DevOps, workload deployment, and service experiments.

## Non-Goals

- Not media-platform-specific deployment
- Not production platform
- Not Kubernetes
- Not HA cluster
- Not OpenCue-specific environment

## Platform Users

| User | Role |
|------|------|
| Hermes | Planner, coordinator, reviewer |
| PVE Agent | Executor with PVE access |
| Workload Teams/Agents | Deploy and verify workloads |
| Verification Agents | Validate platform and workloads |
| Human Operator | Approve destructive operations |

## First Workload

media-platform preview (integration/vs1)

## Future Workloads

- OpenCue/render workers
- CI/Forgejo/Woodpecker
- Object storage experiments
- Observability stack
- Future apps

---

## Capability Catalog

### 1. Compute Capability

| Item | Value |
|------|-------|
| Purpose | Provision VM/LXC |
| Initial | PVE manual/runbook |
| Future | OpenTofu/Ansible/PVE API |
| Interface | compute.vm.create, compute.lxc.create, compute.inventory |

### 2. Network Capability

| Item | Value |
|------|-------|
| Purpose | Management and service connectivity |
| Initial | vmbr0 DHCP + Tailscale |
| Future | lab-entry, Cloudflare Tunnel, vmbr1 |
| Interface | network.management, network.service-route, network.ingress |

### 3. Ingress Capability

| Item | Value |
|------|-------|
| Purpose | Expose internal services safely |
| Initial | Tailscale-only |
| Future | Cloudflare Tunnel, Caddy, preview domain |
| Interface | ingress.tailscale, ingress.preview-domain, ingress.http-route |

### 4. App Deployment Capability

| Item | Value |
|------|-------|
| Purpose | Deploy branch-based applications |
| Initial | Dokploy |
| Interface | app.deploy, app.branch-deploy, app.env, app.health |

### 5. Data Service Capability

| Item | Value |
|------|-------|
| Purpose | Provision databases |
| Initial | Preview PostgreSQL |
| Future | Object storage, Redis |
| Interface | data.postgres.preview, data.object-storage.bucket |

### 6. Secret/Config Capability

| Item | Value |
|------|-------|
| Purpose | Non-production secret handling |
| Initial | Dokploy secret store / manual |
| Future | SOPS/age |
| Interface | secret.preview-env, secret.inject |

### 7. Verification Capability

| Item | Value |
|------|-------|
| Purpose | Validate platform and workloads |
| Initial | Health checks, API-TEST.1, PVE-VERIFY |
| Interface | verify.health, verify.api-smoke, verify.deployment |

### 8. Workload Onboarding Capability

| Item | Value |
|------|-------|
| Purpose | Onboard new apps/workloads |
| Initial | Runbook template |
| Interface | workload.onboard, workload.requirements |

---

## Platform Layers

| Layer | Name | Components | Owner |
|-------|------|------------|-------|
| 0 | Physical/PVE | PVE node, thin_pool, vmbr0, Tailscale | Human |
| 1 | Foundation | lab-entry, lab-control, Dokploy VM, preview DB | PVE Agent |
| 2 | Capabilities | Compute, network, ingress, app, data, secrets, verify | Platform |
| 3 | Workloads | media-platform, OpenCue, CI, storage, observability | Workload teams |
| 4 | Control | Hermes, PVE agent, human approval, future IaC | Mixed |

---

## Exposure Model

### Stage 0 (Current)
- Markdown runbooks
- Reports
- Manual/PVE-agent execution

### Stage 1 (Next)
- OpenTofu skeleton
- Ansible playbooks
- Env templates

### Stage 2 (Future)
- MCP tools
- Verification agents
- Capability registry

---

## Execution Responsibility Model

| Role | Allowed | Forbidden |
|------|---------|-----------|
| Hermes | Plan, decompose, review, coordinate | Direct PVE mutation |
| PVE Agent | Execute approved runbooks, return reports | Self-expand architecture |
| Human | Approve destructive ops, network, DNS, secrets | — |
| IaC Agent | Apply plans after approval | Unapproved changes |

---

## Platform MVP

### Included

- PVE access via Tailscale
- Compute creation standard
- Dokploy app deployment capability
- Preview PostgreSQL capability
- Tailscale-only ingress
- Secret/env handling rules
- API smoke verification
- media-platform preview as first workload

### Excluded

- vmbr1
- Cloudflare Tunnel
- Object storage
- OpenCue
- Forgejo/Woodpecker
- Observability stack
- Kubernetes
- HA
- Production deployment

---

## media-platform Workload Contract

### Required Capabilities

- app.deploy.branch (integration/vs1)
- data.postgres.preview
- secret.preview-env
- ingress.tailscale
- verify.api-smoke (API-TEST.1)

### Not Required for VS.1

- Frontend
- OpenCue
- Object storage
- Cloudflare public ingress
- vmbr1
- Render workers

---

## Task Sequence

| # | Task | Description |
|---|------|-------------|
| 1 | PLATFORM-ACCESS.0 | ✅ Done (PVE-ACCESS.0) |
| 2 | PLATFORM-COMPUTE.0 | VM/LXC creation standards |
| 3 | PLATFORM-APP-DEPLOY.0 | Dokploy VM + app deployment |
| 4 | PLATFORM-DATA.0 | Preview PostgreSQL |
| 5 | PLATFORM-INGRESS.0 | Tailscale → lab-entry later |
| 6 | WORKLOAD-MEDIA-PREVIEW.0 | Deploy media-platform |
| 7 | PLATFORM-VERIFY.0 | Platform verification |

---

*Generated by Hermes Agent — PLATFORM-FOUNDATION.0*
