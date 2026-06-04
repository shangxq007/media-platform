# Error Handling Design

> **Last Updated:** 2026-05-18

## Error Response Format

All backend errors return JSON with:

```json
{
  "errorCode": "RENDER-409-001",
  "message": "Quota exceeded for tenant tenant-123",
  "details": {
    "tenantId": "tenant-123",
    "featureCode": "render.1080p",
    "limit": 60,
    "used": 60
  },
  "timestamp": "2026-05-18T10:00:00Z"
}
```

## Error Code Format

```
{MODULE}-{HTTP_STATUS}-{SEQUENCE}
```

## Error Code Registry

Error codes are defined in `shared-kernel/src/main/resources/error-codes.json`:

```json
{
  "RENDER-409-001": {
    "numericCode": 409001,
    "messages": {
      "en": "Quota exceeded",
      "zh": "配额已用完"
    },
    "module": "render",
    "status": 409
  }
}
```

## Error Code Modules

| Module | Prefix | Count |
|--------|--------|-------|
| Common | `COMMON-` | — |
| Render | `RENDER-` | — |
| Subtitle | `SUBTITLE-` | — |
| Effect | `EFFECT-` | — |
| Timeline | `TIMELINE-` | — |
| Migration | `MIGRATION-` | — |
| Entitlement | `ENTITLEMENT-` | 7 |
| Feature Flag | `FF-` | 13 |
| NLQ | `NLQ-` | 11 |
| Monitoring | `MONITORING-` | 2 |
| Feedback | `FEEDBACK-` | 2 |
| **Total** | | **60+** |

## Exception Hierarchy

```
RuntimeException
└── PlatformException
    ├── errorCode: ErrorCode
    ├── details: Map<String, Object>
    └── locale: String
```

## Global Exception Handler

| Exception Type | HTTP Status | Error Code |
|----------------|-------------|------------|
| `PlatformException` | From errorCode | From exception |
| `IllegalArgumentException` | 400 | `COMMON-400-001` |
| `IllegalStateException` | 409 | `COMMON-409-001` |
| `Exception` | 500 | `COMMON-500-001` |

## i18n Support

- Backend: `ErrorCodeRegistry` loads messages from `error-codes.json`
- Frontend: `useI18nError()` composable provides `t(errorCode)`
- Locale detection: `Accept-Language` header
- Supported languages: English (en), Chinese (zh)
