# Error Handling Design

> **Last updated**: 2026-01-11

## Architecture

All backend errors return JSON with:
```json
{
  "errorCode": "SUBTITLE-400-001",
  "message": "Subtitle parsing failed",
  "details": { "fileName": "subs.srt", "line": 42 },
  "timestamp": "2026-01-11T10:00:00Z"
}
```

## Error Code Format

```
{MODULE}-{STATUS}-{SEQUENCE}
```

| Module | Prefix | Examples |
|--------|--------|----------|
| Common | COMMON- | COMMON-400-001 |
| Render | RENDER- | RENDER-500-001 |
| Subtitle | SUBTITLE- | SUBTITLE-400-001 |
| Effect | EFFECT- | EFFECT-403-001 |
| Timeline | TIMELINE- | TIMELINE-422-001 |
| Migration | MIGRATION- | MIGRATION-409-001 |

## Configuration

Error codes are loaded from `error-codes.json`:
```json
{
  "SUBTITLE-400-001": {
    "numericCode": 400201,
    "messages": { "en": "Subtitle parsing failed", "zh": "字幕解析失败" },
    "module": "subtitle",
    "status": 400
  }
}
```

## i18n Support

- Backend: `ErrorCodeRegistry` loads messages from config
- Frontend: `useI18nError()` composable provides `t(errorCode)`
- Locale detection: `Accept-Language` header

## Exception Hierarchy

```
RuntimeException
└── PlatformException
    ├── errorCode: ErrorCode
    ├── details: Map<String, Object>
    └── locale: String
```

## GlobalExceptionHandler

- `PlatformException` → ProblemDetail with errorCode + localized message + details
- `IllegalArgumentException` → COMMON-400-001
- `Exception` → COMMON-500-001
