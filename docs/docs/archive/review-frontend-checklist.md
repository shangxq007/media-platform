# Frontend Review Checklist

> **Purpose:** Verify all frontend components and their backend consistency.  
> **Reviewer:** _______________  
> **Date:** _______________  
> **Last Updated:** 2026-05-16 (Prompt 62 — added Render/Artifact, Upload/Demo, ErrorState)

---

## Timeline Editor

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Video track renders clips | ⬜ | |
| 2 | Audio track renders waveforms | ⬜ | |
| 3 | Text track renders text elements | ⬜ | |
| 4 | Playhead draggable | ⬜ | |
| 5 | Clip selection and drag | ⬜ | |
| 6 | Zoom in/out timeline | ⬜ | |
| 7 | Undo/redo history | ⬜ | |
| 8 | OTIO export from timeline | ⬜ | |

## Clip Library

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Clip list renders | ⬜ | |
| 2 | Clip drag to timeline | ⬜ | |
| 3 | Clip metadata display | ⬜ | |
| 4 | Search filter works | ⬜ | |
| 5 | Type filter (All/Video/Audio/Image/Subtitle/Text) | ⬜ | |
| 6 | Insert at playhead button | ⬜ | |
| 7 | Delete clip button | ⬜ | |

## Empty Project Guide

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Renders when no clips exist | ⬜ | |
| 2 | Upload Files button triggers file input | ⬜ | |
| 3 | Try Demo Project loads demo timeline | ⬜ | |
| 4 | Import Subtitle button triggers file input | ⬜ | |
| 5 | Shows supported formats text | ⬜ | |

## Media Upload

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Dropzone renders in Clip Library | ⬜ | |
| 2 | Drag over highlights dropzone | ⬜ | |
| 3 | Click opens file browser | ⬜ | |
| 4 | Upload progress list shows active uploads | ⬜ | |
| 5 | Cancel upload button works | ⬜ | |
| 6 | Clear completed button appears | ⬜ | |
| 7 | Probe extracts duration for video/audio | ⬜ | |
| 8 | Probe extracts resolution for video | ⬜ | |
| 9 | Error state shown for failed probes | ⬜ | |

## Demo Project

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Demo project loads 3 clips (2 video, 1 audio) | ⬜ | |
| 2 | Demo clips populate the Clip Library | ⬜ | |
| 3 | Demo clips are placed on timeline tracks | ⬜ | |
| 4 | Demo subtitles load with 4 cues | ⬜ | |
| 5 | Demo effects are applied to clips | ⬜ | |
| 6 | Demo transitions link video clips | ⬜ | |

## Effects Panel

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Effect list renders by tier | ⬜ | |
| 2 | Apply effect to clip | ⬜ | |
| 3 | Effect parameter editing | ⬜ | |
| 4 | Effect pack selection | ⬜ | |
| 5 | Premium effects locked for FREE tier | ⬜ | |

## Effect Pack

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Basic pack available for FREE | ⬜ | |
| 2 | Pro pack available for PRO+ | ⬜ | |
| 3 | Team pack available for TEAM+ | ⬜ | |
| 4 | Enterprise pack available for ENTERPRISE | ⬜ | |
| 5 | Effect compatibility indicator | ⬜ | |

## Subtitle Timeline

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Subtitle cues render on timeline | ⬜ | |
| 2 | Cue drag to adjust timing | ⬜ | |
| 3 | Cue text editing | ⬜ | |
| 4 | Multi-language track display | ⬜ | |
| 5 | Font assignment per track | ⬜ | |

## Export Panel

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Format selection (MP4, WebM, MOV) | ⬜ | |
| 2 | Resolution presets filtered by tier | ⬜ | |
| 3 | Estimated cost display | ⬜ | |
| 4 | Budget status bar | ⬜ | |
| 5 | Anomaly warning display | ⬜ | |
| 6 | Recommended preset button | ⬜ | |
| 7 | GPU indicator for GPU presets | ⬜ | |
| 8 | Worker status (Local/Remote) | ⬜ | |
| 9 | Upgrade options display | ⬜ | |
| 10 | Submit render button | ⬜ | |
| 11 | Recent jobs list | ⬜ | |

## Worker Status UI

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Local worker status indicator | ⬜ | |
| 2 | Remote worker list | ⬜ | |
| 3 | Worker status (IDLE/BUSY/OFFLINE) | ⬜ | |
| 4 | Active jobs per worker | ⬜ | |
| 5 | Worker refresh button | ⬜ | |

## Prompt Management UI

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Template list with search/filter | ⬜ | |
| 2 | Template creation | ⬜ | |
| 3 | Template editing (name, description, category) | ⬜ | |
| 4 | Template body editing | ⬜ | |
| 5 | Version history display | ⬜ | |
| 6 | Version diff view | ⬜ | |
| 7 | Rollback to version | ⬜ | |
| 8 | Render preview with variables | ⬜ | |
| 9 | Redacted output for sensitive vars | ⬜ | |
| 10 | Missing variable warnings | ⬜ | |
| 11 | Risk analysis (risk badge, action) | ⬜ | |
| 12 | Secret detection warning | ⬜ | |
| 13 | Destructive command warning | ⬜ | |
| 14 | File scan/import | ⬜ | |
| 15 | Manifest validation | ⬜ | |

## FeedbackButton

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Feedback button visible (bottom-right) | ⬜ | |
| 2 | Modal opens on click | ⬜ | |
| 3 | Type selection (bug/feature/other) | ⬜ | |
| 4 | Severity selection (low/med/high/critical) | ⬜ | |
| 5 | Title and description fields | ⬜ | |
| 6 | Submit to OpenReplay | ⬜ | |
| 7 | Success/error feedback | ⬜ | |
| 8 | Monitoring status indicator | ⬜ | |

## MonitoringStatus

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Sentry status indicator | ⬜ | |
| 2 | OpenReplay status indicator | ⬜ | |
| 3 | Session ID display | ⬜ | |
| 4 | Session replay URL link | ⬜ | |

## Render Job Status (Prompt 62)

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Job ID displayed | ⬜ | |
| 2 | Running status shows progress bar | ⬜ | |
| 3 | Queued status shows "In queue" | ⬜ | |
| 4 | Failed status shows error + error code | ⬜ | |
| 5 | Failed status shows Retry button | ⬜ | |
| 6 | Running/Queued shows Cancel button | ⬜ | |
| 7 | Cancel shows confirmation dialog | ⬜ | |
| 8 | Completed status hides Retry/Cancel | ⬜ | |
| 9 | Copy diagnostic info button (failed) | ⬜ | |
| 10 | Cancelled status shows neutral badge | ⬜ | |

## Artifact Result (Prompt 62)

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Artifact name displayed | ⬜ | |
| 2 | Format badge shown (MP4, WebM, etc.) | ⬜ | |
| 3 | File size formatted (B/KB/MB) | ⬜ | |
| 4 | Duration formatted as mm:ss | ⬜ | |
| 5 | Resolution shown when available | ⬜ | |
| 6 | Provider displayed | ⬜ | |
| 7 | Completed badge shown | ⬜ | |
| 8 | Preview button emits event | ⬜ | |
| 9 | Download button emits event | ⬜ | |
| 10 | Open in Catalog link (when catalogId present) | ⬜ | |
| 11 | Render Logs link (when URL present) | ⬜ | |
| 12 | Video icon for mp4, audio icon for mp3, image icon for png | ⬜ | |

## Artifact Preview Modal (Prompt 62)

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Does not render when closed | ⬜ | |
| 2 | Renders when open with video artifact | ⬜ | |
| 3 | Shows `<video>` element for mp4 | ⬜ | |
| 4 | Shows `<audio>` element for mp3 | ⬜ | |
| 5 | Shows `<img>` element for png | ⬜ | |
| 6 | Shows fallback for unsupported format | ⬜ | |
| 7 | Displays artifact name in header | ⬜ | |
| 8 | Displays format/resolution in footer | ⬜ | |
| 9 | Close button emits close event | ⬜ | |
| 10 | Renders with null artifact when open | ⬜ | |

## ErrorState (Prompt 62)

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Default error message renders | ⬜ | |
| 2 | Custom title and description render | ⬜ | |
| 3 | Error code displayed when provided | ⬜ | |
| 4 | Diagnostic ID with copy button | ⬜ | |
| 5 | Retry button visible by default | ⬜ | |
| 6 | Retry button hidden when showRetry=false | ⬜ | |
| 7 | Dismiss button hidden when showDismiss=false | ⬜ | |
| 8 | Retry event emitted on click | ⬜ | |
| 9 | Dismiss event emitted on click | ⬜ | |
| 10 | Admin debug toggle shows/hides debug info | ⬜ | |
| 11 | Error details displayed when provided | ⬜ | |

## ErrorCode i18n

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Error codes display translated messages | ⬜ | |
| 2 | English (en) translations | ⬜ | |
| 3 | Chinese (zh) translations | ⬜ | |
| 4 | Fallback to error code if no translation | ⬜ | |
| 5 | RENDER-500-001 on render failure | ⬜ | |
| 6 | COST-402-001 on budget exceeded | ⬜ | |
| 7 | ENTITLEMENT-403-001 on tier violation | ⬜ | |
| 8 | PROMPT-403-001 on safety block | ⬜ | |

## Sensitive Input Desensitization

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | API keys not sent to monitoring | ⬜ | |
| 2 | Passwords not sent to monitoring | ⬜ | |
| 3 | Tokens not sent to monitoring | ⬜ | |
| 4 | Prompt sensitive variables redacted | ⬜ | |
| 5 | Request headers sanitized | ⬜ | |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| Timeline Editor | ___/8 | 8 | |
| Clip Library | ___/7 | 7 | |
| Empty Project Guide | ___/5 | 5 | |
| Media Upload | ___/9 | 9 | |
| Demo Project | ___/6 | 6 | |
| Effects Panel | ___/5 | 5 | |
| Effect Pack | ___/5 | 5 | |
| Subtitle Timeline | ___/5 | 5 | |
| Export Panel | ___/11 | 11 | |
| Render Job Status | ___/10 | 10 | |
| Artifact Result | ___/12 | 12 | |
| Artifact Preview Modal | ___/10 | 10 | |
| ErrorState | ___/11 | 11 | |
| Worker Status | ___/5 | 5 | |
| Prompt Management | ___/15 | 15 | |
| FeedbackButton | ___/8 | 8 | |
| MonitoringStatus | ___/4 | 4 | |
| ErrorCode i18n | ___/8 | 8 | |
| Desensitization | ___/5 | 5 | |
| **Total** | ___/149 | **149** | |

**Reviewer Signature:** _______________  
**Date:** _______________
