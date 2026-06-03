# Monitoring and Feedback Review Checklist

> **Purpose:** Verify Sentry + OpenReplay integration and feedback system.  
> **Reviewer:** _______________  
> **Date:** _______________

---

## Sentry Frontend Integration

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Sentry SDK initializes with DSN | ⬜ | |
| 2 | Sentry disabled when no DSN | ⬜ | |
| 3 | Session Replay configured | ⬜ | |
| 4 | User context set (tenantId, userId) | ⬜ | |
| 5 | RenderJob context set | ⬜ | |
| 6 | PromptExecution context set | ⬜ | |
| 7 | Provider context set | ⬜ | |
| 8 | Exception capture works | ⬜ | |
| 9 | Message capture works | ⬜ | |
| 10 | Replay ID retrievable | ⬜ | |

## Sentry Frontend Desensitization

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | API keys redacted in events | ⬜ | |
| 2 | Passwords redacted in events | ⬜ | |
| 3 | Tokens redacted in events | ⬜ | |
| 4 | Request headers sanitized | ⬜ | |
| 5 | Request body sanitized | ⬜ | |
| 6 | Stack trace vars sanitized | ⬜ | |
| 7 | Breadcrumb data sanitized | ⬜ | |

## OpenReplay Frontend Integration

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | OpenReplay initializes with project key | ⬜ | |
| 2 | OpenReplay disabled when no key | ⬜ | |
| 3 | Session recording active | ⬜ | |
| 4 | User metadata set | ⬜ | |
| 5 | Custom events recorded | ⬜ | |
| 6 | Session ID retrievable | ⬜ | |
| 7 | Session URL generated | ⬜ | |

## OpenReplay Desensitization

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Text input sanitized | ⬜ | |
| 2 | Input field sanitized | ⬜ | |
| 3 | Change events sanitized | ⬜ | |
| 4 | Network data sanitized | ⬜ | |
| 5 | Sensitive headers redacted | ⬜ | |

## Sentry Backend Integration

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | SentryMonitoringService captures exceptions | ⬜ | |
| 2 | RenderPipeline exception with context | ⬜ | |
| 3 | Provider exception with context | ⬜ | |
| 4 | Remote Worker exception with context | ⬜ | |
| 5 | Prompt execution exception with context | ⬜ | |
| 6 | User context set | ⬜ | |
| 7 | Tags set | ⬜ | |
| 8 | Disabled by default | ⬜ | |

## Global Exception Handler

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | IllegalArgumentException → 400 | ⬜ | |
| 2 | IllegalStateException → 409 | ⬜ | |
| 3 | General exception → 500 | ⬜ | |
| 4 | All exceptions sent to Sentry | ⬜ | |
| 5 | ProblemDetail response | ⬜ | |
| 6 | Timestamp in response | ⬜ | |
| 7 | Works without Sentry bean | ⬜ | |

## RenderJob ID Association

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Frontend sets renderJobId context | ⬜ | |
| 2 | Backend captures renderJobId | ⬜ | |
| 3 | Sentry event includes renderJobId | ⬜ | |
| 4 | OpenReplay event includes renderJobId | ⬜ | |

## PromptExecutionRun ID Association

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Frontend sets promptExecutionId context | ⬜ | |
| 2 | Backend captures promptExecutionId | ⬜ | |
| 3 | Sentry event includes promptExecutionId | ⬜ | |

## Provider / Worker Context

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Provider key in Sentry context | ⬜ | |
| 2 | Worker ID in Sentry context | ⬜ | |
| 3 | Tenant ID in Sentry context | ⬜ | |
| 4 | User ID in Sentry context | ⬜ | |

## Alert / Notification

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Critical exceptions trigger notification | ⬜ | Stub |
| 2 | Alert includes context (jobId, tenantId) | ⬜ | Stub |
| 3 | MONITORING-500-001 on monitoring error | ⬜ | |
| 4 | MONITORING-503-001 on replay unavailable | ⬜ | |

## Environment Variables

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | VITE_SENTRY_DSN for frontend | ⬜ | |
| 2 | SENTRY_DSN for backend | ⬜ | |
| 3 | SENTIRONMENT for environment | ⬜ | |
| 4 | SENTRY_ENABLED flag | ⬜ | |
| 5 | VITE_OPENREPLAY_PROJECT_KEY | ⬜ | |
| 6 | OPENREPLAY_ENABLED flag | ⬜ | |
| 7 | No hardcoded DSNs/keys | ⬜ | |

## Default Closed Strategy

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Sentry disabled by default | ⬜ | |
| 2 | OpenReplay disabled by default | ⬜ | |
| 3 | No errors when SDKs not installed | ⬜ | |
| 4 | Graceful fallback | ⬜ | |

## Troubleshooting Guide

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | How to find session by RenderJob ID | ⬜ | |
| 2 | How to find session by PromptExecution ID | ⬜ | |
| 3 | How to verify desensitization | ⬜ | |
| 4 | How to disable monitoring | ⬜ | |
| 5 | How to check monitoring status | ⬜ | |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| Sentry Frontend | ___/10 | 10 | |
| Sentry Desensitization | ___/7 | 7 | |
| OpenReplay Frontend | ___/7 | 7 | |
| OpenReplay Desensitization | ___/5 | 5 | |
| Sentry Backend | ___/8 | 8 | |
| Exception Handler | ___/7 | 7 | |
| RenderJob Association | ___/4 | 4 | |
| PromptExecution Association | ___/3 | 3 | |
| Provider/Worker Context | ___/4 | 4 | |
| Alert/Notification | ___/4 | 4 | |
| Environment Variables | ___/7 | 7 | |
| Default Closed | ___/4 | 4 | |
| Troubleshooting | ___/5 | 5 | |
| **Total** | ___/75 | **75** | |

**Reviewer Signature:** _______________  
**Date:** _______________
