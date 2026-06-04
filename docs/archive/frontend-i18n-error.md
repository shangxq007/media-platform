# Frontend i18n Error Handling

> **Last updated**: 2026-01-11

## Usage

```typescript
import { useI18nError } from '@/utils/i18n'

const { t, setLocale } = useI18nError()

// In error handler:
const message = t('SUBTITLE-400-001') // "Subtitle parsing failed"
const messageZh = getErrorMessage('SUBTITLE-400-001', 'zh') // "字幕解析失败"
```

## Error Response Handling

API interceptor automatically localizes error messages:

```typescript
api.interceptors.response.use(
  resp => resp,
  err => {
    const errorData = err.response?.data as ErrorResponse
    if (errorData?.errorCode) {
      errorData.message = getErrorMessage(errorData.errorCode)
    }
    return Promise.reject(err)
  }
)
```

## Supported Locales

| Locale | Code |
|--------|------|
| English | en |
| Chinese | zh |

## Error Code Categories

| Category | Prefix | User Message |
|----------|--------|--------------|
| Common | COMMON- | Generic errors |
| Render | RENDER- | Render job failures |
| Subtitle | SUBTITLE- | Subtitle parsing/upload errors |
| Effect | EFFECT- | Effect compatibility errors |
| Timeline | TIMELINE- | Timeline data errors |
| Migration | MIGRATION- | Schema migration errors |

## Tier-Based Error Messages

Some errors include tier information:
- `EFFECT-403-001`: "Effect not available for current tier"
- User sees upgrade prompt based on error code
