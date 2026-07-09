# FFmpeg Worker Deployment

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-DEPLOY.0

---

## Image Strategy

**Strategy:** Dedicated Dockerfile

**Image:** `ghcr.io/shangxq007/platform-ffmpeg-worker:latest`

**Base:** `eclipse-temurin:25-jre-jammy`

**Dependencies:**
- FFmpeg 4.4.2
- libass9
- fontconfig
- fonts-dejavu-core
- fonts-noto-cjk

---

## Worker Runtime Command

```bash
docker run --rm \
  -e DATABASE_URL=jdbc:postgresql://... \
  -e DATABASE_USERNAME=... \
  -e DATABASE_PASSWORD=... \
  -e RENDER_WORKER_JOB_ID=rj_xxx \
  ghcr.io/shangxq007/platform-ffmpeg-worker:latest
```

---

## Environment Contract

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | JDBC URL |
| `DATABASE_USERNAME` | DB username |
| `DATABASE_PASSWORD` | DB password |
| `RENDER_WORKER_JOB_ID` | Job ID to execute |
| `RENDER_WORKER_ENABLED` | Enable worker (default: true) |

---

## Verification

| Check | Result |
|-------|--------|
| FFmpeg available | ✅ 4.4.2 |
| libass available | ✅ |
| Fonts available | ✅ |
| Java 25 | ✅ |
| Worker profile | ✅ |

---

## Status

- RENDER-WORKER-DEPLOY.0: COMPLETE
- Worker image: BUILT
- FFmpeg verified: YES
