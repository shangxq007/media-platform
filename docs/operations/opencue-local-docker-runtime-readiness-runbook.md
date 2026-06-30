# OpenCue Local Docker Runtime Readiness Runbook

## 1. Scope

Operator-run runbook for P2O.0c OpenCue image selection and local Docker runtime readiness validation.

## 2. Prerequisites

- Docker v20+ installed
- Docker Compose v2+ installed
- Repository cloned
- Confirmed images: opencue/cuebot:1.19.1, opencue/rqd:1.19.1

## 3. Image Requirements

| Component | Image | Notes |
|-----------|-------|-------|
| PostgreSQL | postgres:16-alpine | Confirmed |
| Cuebot | opencue/cuebot:1.19.1 | Java, gRPC port 8443 |
| RQD (base) | opencue/rqd:1.19.1 | Rust openrqd, NO ffmpeg |
| RQD (smoke) | opencue-rqd-smoke:local | Local build with ffmpeg |

## 4. Shared Path Setup

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/prepare-runtime-ready.sh
```

## 5. Compose Config Validation

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/validate-compose-config.sh
```

## 6. Runtime Startup

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh
```

## 7. Cuebot Checks

```bash
docker logs opencue-cuebot --tail=20
docker exec opencue-postgres psql -U opencue -d opencue -c "SELECT str_name FROM host;"
```

## 8. RQD Checks

```bash
docker logs opencue-rqd --tail=20
```

## 9. Shared Path Mount Check

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-rqd-shared-path.sh
```

## 10. ffmpeg/ffprobe Check

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-rqd-ffmpeg.sh
```

## 11. Log Collection

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/collect-runtime-logs.sh
```

## 12. Stop/Cleanup

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/stop-runtime.sh
```

## 13. Troubleshooting

| Issue | Solution |
|-------|----------|
| Cuebot JDBC error | Check CUEBOT_DB_URL env var |
| RQD connects to localhost | Check OPENCUE_RQD_CONFIG env var |
| Tables don't exist | Apply schema migrations |
| Host not registered | Create seed data (facility, alloc) |
| ffmpeg not found | Rebuild RQD smoke image |

## 14. What Not to Do

- Do not use in production
- Do not push images
- Do not run untrusted scripts

## 15. Transition to P2O.0d

P2O.0d will implement actual OpenCue job submission using the runtime validated here.

## 16. Transition to PVE

Same images, different mount point. Replace Docker named volume with host bind mount.
