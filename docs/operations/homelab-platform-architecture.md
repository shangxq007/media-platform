# PVE General DevOps Platform Architecture Plan

**Date:** 2026-07-02
**Status:** PLANNED
**Authority:** HOMELAB-PLAN.0

---

## Phase A — Platform Goals

### Goals

- General DevOps/homelab platform
- media-platform preview deployment
- Future OpenCue/render worker experiments
- Future CI/Forgejo/Woodpecker
- Future object storage
- Future observability
- Future IaC/OpenTofu/Ansible control

### Non-Goals

- No production-grade HA yet
- No Kubernetes unless explicitly justified
- No PVE cluster yet
- No automatic PVE network mutation
- No OpenCue deployment in current preview path
- No frontend deployment for VS.1
- No production secrets

---

## Phase B — VM/LXC Layout

### Resource Budget

| Resource | Total | Reserved for PVE | Usable |
|----------|-------|------------------|--------|
| RAM | 31GiB | 6-8GiB | ~23GiB |
| CPU | 44T | 2-4T | ~40T |
| Storage | ~5.5T | minimal | ~5.4T |

### Recommended VM/LXC Table

| ID | Name | Type | Role | CPU | RAM | Disk | Managed by Dokploy |
|----|------|------|------|-----|-----|------|-------------------|
| 110 | lab-entry | LXC | Tailscale/Tunnel/Caddy/ingress | 1-2 | 1-2GB | 24-32GB | NO |
| 101 | lab-control | VM | OpenTofu/Ansible/MCP | 4 | 6-8GB | 100-160GB | NO |
| 121 | forgejo-ci | VM | Forgejo + Woodpecker | 4 | 6-8GB | 160-240GB | NO |
| 122 | object-storage | VM | RustFS/Garage/SeaweedFS | 4 | 4-8GB | 300GB+ | NO |
| 123 | dokploy | VM | App deployment platform | 4 | 6-8GB | 120-200GB | self |
| 124 | ci-runner-01 | VM | CI runner | 4 | 4-8GB | 80-160GB | NO |
| 125 | monitoring | VM | OTel/Prometheus/Grafana | 2-4 | 4GB+ | 100GB+ | NO |
| 161 | media-opencue | VM | OpenCue smoke (future) | 8 | 12GB | 160-240GB | NO |
| 162 | media-preview | Dokploy app | media-platform preview | 4 | 6-8GB | 80-160GB | YES |
| 201+ | render-worker-* | VM | FFmpeg/render workers | 4-8 | 4-12GB | 80-160GB | NO |

### First Wave (Minimal)

| ID | Name | Priority | Reason |
|----|------|----------|--------|
| 110 | lab-entry | HIGH | Ingress/Tailscale entry |
| 101 | lab-control | MEDIUM | IaC control plane |
| 123 | dokploy | HIGH | App deployment |
| — | preview-db | HIGH | PostgreSQL for preview |
| 162 | media-preview | HIGH | VS.1 preview app |

---

## Phase C — Network Architecture

### Stage 0 (Current)

| Item | Value |
|------|-------|
| Bridge | vmbr0 |
| IP | DHCP |
| Management | Tailscale 100.122.122.110 |
| VLAN | None |
| vmbr1 | Does not exist |

### Stage 1 (Recommended Next)

| Item | Value |
|------|-------|
| Ingress | Cloudflare Tunnel via lab-entry |
| Routing | Caddy reverse proxy |
| Preview domain | *.preview.scribe.cc.cd or similar |
| Internal DNS | Not required yet |

### Stage 2 (Future)

| Item | Value |
|------|-------|
| vmbr1 | 10.10.10.0/24 (internal lab bridge) |
| VLAN | Optional segmentation |
| Service discovery | Internal DNS or Consul |

---

## Phase D — Storage Architecture

### Current PVE Storage

| Storage | Type | Size | Use |
|---------|------|------|-----|
| local | dir | small | ISO/templates |
| local-lvm | lvm | ~141GB | VM disks (small) |
| thin_pool | lvm-thin | ~5.5T | Main VM disk pool |

### Recommendations

| Data Type | Storage | Notes |
|-----------|---------|-------|
| VM disks | thin_pool | Main pool |
| ISO/templates | local | Small files |
| Docker volumes | VM disk | On thin_pool |
| Database data | VM disk | On thin_pool |
| Object storage | Dedicated VM disk | 300GB+ on thin_pool |
| Render temp | Disposable | No backup needed |
| Artifacts | Isolated | Not on PVE host root |

### Object Storage Evaluation

| Option | Status | Recommendation |
|--------|--------|----------------|
| RustFS | Candidate | First choice (Rust, lightweight) |
| Garage | Candidate | Alternative |
| SeaweedFS | Candidate | Alternative |
| MinIO | Known | Not recommended (AGPL, heavy) |
| Cloudflare R2 | External | Already used for reports |

**Required before VS.1 preview:** NO (local storage sufficient)

---

## Phase E — Service Ownership Matrix

| Service | Owner Layer | Dokploy? | Stateful | Backup | Exposure | Status |
|---------|-------------|----------|----------|--------|----------|--------|
| PVE host | PVE | NO | YES | HIGH | Tailscale | READY |
| lab-entry | LXC | NO | NO | LOW | Tailscale/Tunnel | PLANNED |
| lab-control | VM | NO | YES | HIGH | Tailscale | PLANNED |
| Dokploy | VM | self | YES | HIGH | Tailscale | PLANNED |
| PostgreSQL preview | VM/Docker | NO | YES | MEDIUM | Internal | PLANNED |
| Redis | Docker | NO | NO | LOW | Internal | PLANNED |
| Object storage | VM | NO | YES | HIGH | Internal | PLANNED |
| media-platform | Dokploy app | YES | NO | LOW | Preview domain | PLANNED |
| Forgejo | VM | NO | YES | HIGH | Tailscale | PLANNED |
| Woodpecker | VM | NO | NO | LOW | Tailscale | PLANNED |
| OpenCue | VM | NO | YES | MEDIUM | Internal | PLANNED |
| Render workers | VM | NO | NO | LOW | Internal | PLANNED |
| Observability | VM | NO | YES | MEDIUM | Tailscale | PLANNED |

---

## Phase F — media-platform Preview Deployment

### Target Flow

```
API-TEST.1
  → preview.domain
  → Cloudflare Tunnel / lab-entry
  → Dokploy app
  → media-platform preview
  → preview PostgreSQL
  → optional artifact storage
```

### Configuration

| Item | Value |
|------|-------|
| Branch | integration/vs1 |
| Manager | Dokploy |
| Profile | preview |
| Health | /healthz |
| Smoke | API-TEST.1 |
| Database | Independent preview PostgreSQL |
| Storage | Local initially |
| Domain | preview subdomain |
| Frontend | Not required |
| OpenCue | Not required |

---

## Phase G — Security Plan

| Item | Rule |
|------|------|
| Production secrets | Never used in preview |
| Preview DB creds | Isolated, least privilege |
| JWT secret | Preview-only, separate |
| Dokploy token | Scoped, not printed |
| Cloudflare token | Scoped, not printed |
| SSH keys | Key-based only |
| Secret storage | Manual → SOPS/age later |
| Agent access | Read-only unless assigned |

---

## Phase H — Rollout Sequence

| # | Task | Description | Status |
|---|------|-------------|--------|
| 1 | PVE-ACCESS.0 | Configure SSH key access | PLANNED |
| 2 | DOKPLOY-INSTALL.0 | Install Dokploy VM | PLANNED |
| 3 | DB-PREVIEW.0 | Create preview PostgreSQL | PLANNED |
| 4 | LAB-ENTRY.0 | Configure ingress/Tunnel | PLANNED |
| 5 | DOKPLOY-PREVIEW.0 | Deploy media-platform preview | PLANNED |
| 6 | PVE-VERIFY.1-RERUN | Run API-TEST.1 against preview | PLANNED |

### Deferred

- Object storage deployment
- OpenCue
- Render workers
- Forgejo/Woodpecker
- Observability
- vmbr1 internal network
- IaC/OpenTofu full automation

---

## Phase I — Decision Points

| # | Question | Decision | Reason |
|---|----------|----------|--------|
| 1 | Install Dokploy now? | YES | Required for preview |
| 2 | Create vmbr1 now? | NO | Keep simple, add later |
| 3 | lab-entry before Dokploy? | NO | Can add Tunnel later |
| 4 | Preview DB location? | Separate VM or Docker | Isolation preferred |
| 5 | Object storage for VS.1? | NO | Local sufficient |
| 6 | OpenCue for VS.1? | NO | Not required |
| 7 | Cloudflare Tunnel before deploy? | NO | Can use Tailscale initially |
| 8 | media-platform as Dokploy app? | YES | Standard deployment |
| 9 | Agent creates infra? | Runbooks only | Safety first |

---

*Generated by Hermes Agent — HOMELAB-PLAN.0*
