# Feedback and Monitoring Integration

## Overview

The media-platform integrates **Sentry** (error monitoring + session replay) and **OpenReplay** (user feedback + session replay) for comprehensive observability.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Sentry SDK   │  │ OpenReplay   │  │ FeedbackButton   │  │
│  │ (Vue plugin) │  │ Tracker      │  │ Component        │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│         │                 │                    │             │
│         └────────┬────────┴────────────────────┘             │
│                  │                                           │
│         ┌────────▼────────┐                                  │
│         │  sentry.ts      │  Exception capture, user context │
│         │  openreplay.ts  │  Session recording, feedback     │
│         └─────────────────┘                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        Backend                               │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │ SentryMonitoring     │  │ GlobalSentryException        │ │
│  │ Service              │  │ Handler                      │ │
│  │ (shared-kernel)      │  │ (platform-app)               │ │
│  └──────────────────────┘  └──────────────────────────────┘ │
│                                                              │
│  Exception context includes:                                 │
│  - renderJobId, promptExecutionId                            │
│  - providerKey, workerId                                     │
│  - tenantId, userId                                          │
└─────────────────────────────────────────────────────────────┘
```

## Frontend Integration

### Sentry + Session Replay

**Configuration** (`frontend/src/utils/sentry.ts`):
- `initSentry()` - Initialize Sentry SDK with DSN and config
- `setSentryUser()` - Set user context (id, tenantId, email)
- `setSentryContext()` - Set RenderJob/PromptExecution context
- `captureSentryException()` - Capture exceptions with context
- `captureSentryMessage()` - Capture messages
- `getSentryReplayId()` - Get current session replay ID
- Automatic sanitization of sensitive data (API keys, passwords, tokens)

**Environment Variables:**
- `VITE_SENTRY_DSN` - Sentry DSN (required to enable)
- `VITE_SENTRY_ENVIRONMENT` - Environment name (default: development)

### OpenReplay

**Configuration** (`frontend/src/utils/openreplay.ts`):
- `initOpenReplay()` - Initialize OpenReplay tracker
- `setOpenReplayUser()` - Set user metadata
- `submitOpenReplayFeedback()` - Submit user feedback with session context
- `recordOpenReplayEvent()` - Record custom events
- `getOpenReplaySessionId()` - Get current session ID
- Automatic text/input sanitization for sensitive data

**Environment Variables:**
- `VITE_OPENREPLAY_PROJECT_KEY` - OpenReplay project key (required to enable)
- `VITE_OPENREPLAY_INGEST` - Ingest endpoint URL

### Feedback UI Components

**FeedbackButton** (`components/feedback/FeedbackButton.vue`):
- Fixed position button in bottom-right corner
- Opens feedback modal with type, severity, title, description
- Submits to OpenReplay with Sentry replay ID for correlation
- Shows monitoring status indicator (green dot = active)

**MonitoringStatus** (`components/feedback/MonitoringStatus.vue`):
- Displays Sentry and OpenReplay status
- Shows session IDs and replay URLs
- Collapsible detail panel

### Data Desensitization

All monitoring data is automatically sanitized:

**Sentry:**
- Request headers: `authorization`, `cookie`, `x-api-key`, `x-auth-token` → `[REDACTED]`
- Request body: API keys, passwords, tokens → `[REDACTED_API_KEY]`, `[REDACTED]`
- Stack trace variables: sensitive keys → `[REDACTED]`

**OpenReplay:**
- Text input sanitization via `textSanitizer`
- Input field sanitization via `inputSanitizer`
- Network request sanitization via `networkSanitizer`
- Headers: sensitive headers → `[REDACTED]`

## Backend Integration

### SentryMonitoringService

**Location:** `shared-kernel/src/main/java/.../shared/monitoring/SentryMonitoringService.java`

**Features:**
- `captureException()` - Capture exception with context map
- `captureMessage()` - Capture message with level
- `setUserContext()` - Set user context
- `setTag()` - Set event tags
- `captureRenderPipelineException()` - RenderJob context
- `captureProviderException()` - Provider context
- `captureRemoteWorkerException()` - Worker context
- `capturePromptExecutionException()` - Prompt execution context

**Configuration** (`application.yml`):
```yaml
sentry:
  dsn: ${SENTRY_DSN:}
  environment: ${SENTRY_ENVIRONMENT:development}
  enabled: ${SENTRY_ENABLED:false}
  traces-sample-rate: ${SENTRY_TRACES_SAMPLE_RATE:1.0}
```

### GlobalSentryExceptionHandler

**Location:** `platform-app/src/main/java/.../app/GlobalSentryExceptionHandler.java`

- Catches all unhandled exceptions
- Sends to Sentry with module context
- Returns structured `ProblemDetail` responses
- Works without Sentry (Optional dependency)

## Error Codes

| Code | Description |
|------|-------------|
| `MONITORING-500-001` | Monitoring service error |
| `MONITORING-503-001` | Session replay service unavailable |
| `FEEDBACK-400-001` | Invalid feedback submission |
| `FEEDBACK-500-001` | Feedback submission failed |

## Usage Examples

### Frontend Error Capture
```typescript
import { captureSentryException, setSentryContext } from '@/utils/sentry'

// Set context for current operation
setSentryContext({ renderJobId: 'job-123', providerKey: 'javacv' })

// Capture exception
try {
  await submitRender(job)
} catch (err) {
  captureSentryException(err as Error, { renderJobId: 'job-123' })
}
```

### Frontend User Feedback
```typescript
import { submitOpenReplayFeedback } from '@/utils/openreplay'

await submitOpenReplayFeedback({
  type: 'bug',
  title: 'Render failed',
  description: 'The render job failed with provider error',
  severity: 'high',
  renderJobId: 'job-123'
})
```

### Backend Exception Capture
```java
@Service
public class RenderService {
    private final SentryMonitoringService sentryMonitoring;

    public void executeRender(String renderJobId) {
        try {
            // render logic
        } catch (Exception e) {
            sentryMonitoring.captureRenderPipelineException(
                e, renderJobId, "javacv", tenantId, userId);
            throw e;
        }
    }
}
```

## Security

- No API keys, secrets, or passwords are sent to Sentry or OpenReplay
- All sensitive data is redacted before transmission
- Session replay excludes password fields and sensitive inputs
- DSN and project keys are configured via environment variables (not hardcoded)
- Monitoring is disabled by default and must be explicitly enabled

## Testing

- `SentryMonitoringServiceTest` - 11 tests for monitoring service
- `GlobalSentryExceptionHandlerTest` - 5 tests for exception handler
- All tests pass with monitoring disabled (default)
