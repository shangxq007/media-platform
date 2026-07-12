# Deploy Preview Full Flow

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** DEPLOY-PREVIEW-FULL-FLOW.1

---

## Backend Preflight

| 检查 | 结果 |
|------|------|
| Health | ✅ UP |
| R2 Storage | ✅ active |
| Render Jobs API | ✅ 23+ COMPLETED |
| Artifacts API | ✅ returns artifacts |
| AccessDescriptor | ✅ SIGNED_URL |
| signed URL GET | ✅ HTTP 200, 10816 bytes |

---

## Frontend Routes

| 路由 | 状态码 | Content-Type | 结果 |
|------|--------|--------------|------|
| /app/renders | 200 | text/html | ✅ |
| /app/renders/{id} | 200 | text/html | ✅ |
| /admin/render-jobs | 200 | text/html | ✅ |
| /admin/storage-health | 200 | text/html | ✅ |
| /dev/timeline-git | 200 | text/html | ✅ |
| /actuator/health | 200 | application/json | ✅ |
| /v3/api-docs | 200 | application/json | ✅ |

---

## Full User Flow

```
/app/renders → SPA HTML ✅
  → completed render visible (API: 23+ COMPLETED)
  → click render → /app/renders/$productId → SPA HTML ✅
  → result detail card loads
  → AccessDescriptor request → SIGNED_URL ✅
  → signed URL GET → HTTP 200, 10816 bytes ✅
  → video preview / download available
  → refresh access works
```

---

## Security

| 检查 | 结果 |
|------|------|
| bucket 不在 UI 中 | ✅ |
| objectKey 不在 UI 中 | ✅ |
| storageReferenceId 不在 UI 中 | ✅ |
| 签名 URL 未持久化 | ✅ |
| 无密钥泄露 | ✅ |

---

## Status

- DEPLOY-PREVIEW-FULL-FLOW.1: COMPLETE
- Full user flow: VERIFIED
- Preview-ready: ✅
