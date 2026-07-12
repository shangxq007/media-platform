# OpenDAL RustFS Lab

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** STORAGE-OPENDAL-RUSTFS-LAB.0

---

## Context

OpenDAL POC: COMPLETE (local fs). User prefers RustFS over MinIO. Current R2 path: KEEP_STABLE.

---

## Lab Setup

**Location:** `infra/lab/rustfs/`

```bash
# Start RustFS
cd infra/lab/rustfs
docker compose up -d

# Check health
curl http://localhost:9000/minio/health/live
```

---

## Configuration

```yaml
storage:
  experimental:
    opendal:
      enabled: true
      backend: s3
      endpoint: http://localhost:9000
      bucket: media-platform-lab
      region: us-east-1
      access-key: rustfsadmin
      secret-key: rustfsadmin
      path-style: true
      mode: poc
```

---

## Status

- OpenDAL RustFS Lab: CREATED
- Lab files: infra/lab/rustfs/
- Current R2 path: UNCHANGED
