# Upload & Demo Project

> **Module:** `frontend/src/components/`
> **Last Updated:** 2026-05-18

## Upload Workflow

```mermaid
graph TB
    USER["User drags file"] --> DROP["MediaUploadDropzone"]
    DROP -->|"validate"| VALID["File Validation"]
    VALID -->|"type check"| TYPE["Check MIME type"]
    VALID -->|"size check"| SIZE["Check file size"]
    TYPE -->|"valid"| PROGRESS["UploadProgressList"]
    SIZE -->|"valid"| PROGRESS
    PROGRESS -->|"POST /api/v1/upload"| BACKEND["Backend"]
    BACKEND -->|"success"| CLIP["Add to Clip Library"]
    CLIP -->|"update"| STORE["Pinia Store"]
```

## Demo Project

The demo project provides a pre-populated project for new users:

```mermaid
graph TB
    USER["New user visits editor"] --> EMPTY["EmptyProjectGuide"]
    EMPTY -->|"click 'Try Demo'"| DEMO["Load Demo Project"]
    DEMO -->|"populate"| CLIPS["Clip Library"]
    DEMO -->|"populate"| TIMELINE["Timeline"]
    CLIPS --> STORE["Pinia Store"]
    TIMELINE --> STORE
```

## Upload Component

| Component | Purpose | Status |
|-----------|---------|--------|
| `MediaUploadDropzone` | Drag-and-drop file upload | ✅ |
| `UploadProgressList` | Upload progress display | ✅ |
| `EmptyProjectGuide` | Empty state with upload/demo | ✅ |

## Supported File Types

| Type | Extensions |
|------|-----------|
| Video | `.mp4`, `.webm`, `.mov`, `.ogg` |
| Audio | `.mp3`, `.wav`, `.aac` |
| Image | `.jpg`, `.png`, `.gif`, `.webp` |
| Subtitle | `.srt`, `.ass`, `.vtt` |
| Font | `.ttf`, `.otf`, `.woff`, `.woff2` |

## Upload Limits

| Limit | Value |
|-------|-------|
| Max file size | Configurable (default 500MB) |
| Max concurrent uploads | 3 |
| Allowed types | Configurable whitelist |
