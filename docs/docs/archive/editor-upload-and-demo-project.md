# Editor: Upload and Demo Project Guide

> **Purpose:** Document the empty project experience, media upload workflow, and demo project feature.  
> **Component:** `EmptyProjectGuide.vue`, `MediaUploadDropzone.vue`, `ClipLibrary.vue`  
> **Last Updated:** 2026-05-16 (Prompt 62)

---

## Starting with an Empty Project

When a user opens the editor without any media files, they are presented with the **Empty Project Guide** (`EmptyProjectGuide.vue`). This component provides three clear entry points:

1. **Upload Files** — Opens a native file browser to select media files
2. **Try Demo Project** — Loads a pre-configured demo project with sample clips, subtitles, effects, and transitions
3. **Import Subtitle** — Opens a file browser specifically for subtitle files (SRT, ASS, VTT)

The Empty Project Guide renders automatically inside the Clip Library panel when:
- No clips exist in the timeline store (`timelineStore.clips` is empty)
- No uploads are in progress (`uploadItems` is empty)

### Empty Project Guide Layout

```
┌──────────────────────────────────────────┐
│                  🎬                      │
│          Start Your Project              │
│                                          │
│   Upload media files to start editing.   │
│   You can also try a demo project to     │
│   explore the editor.                    │
│                                          │
│   ┌──────────────────────────────────┐   │
│   │  📁 Upload Files                 │   │
│   └──────────────────────────────────┘   │
│   ┌──────────────────────────────────┐   │
│   │  ✨ Try Demo Project             │   │
│   └──────────────────────────────────┘   │
│   ┌──────────────────────────────────┐   │
│   │  📝 Import Subtitle              │   │
│   └──────────────────────────────────┘   │
│                                          │
│   Supports: MP4, MOV, AVI, MP3, WAV,    │
│             SRT, ASS, VTT                │
└──────────────────────────────────────────┘
```

---

## Uploading Media Files

### Upload via Empty Project Guide

Clicking "Upload Files" triggers a hidden `<input type="file">` element with:
- `multiple` — Allows selecting multiple files at once
- `accept="video/*,audio/*,.srt,.ass,.vtt"` — Filters to supported formats

### Upload via Clip Library Dropzone

The **Media Upload Dropzone** (`MediaUploadDropzone.vue`) is always visible at the bottom of the Clip Library panel. It supports:

- **Drag and drop** — Files can be dragged from the OS file manager
- **Click to browse** — Clicking the dropzone opens a native file browser
- **Keyboard activation** — Pressing Enter or Space when focused triggers the file browser

#### Accepted File Types

| Category | MIME Types / Extensions |
|----------|------------------------|
| Video    | `video/*` (MP4, MOV, AVI, WebM, etc.) |
| Audio    | `audio/*` (MP3, WAV, AAC, OGG, etc.) |
| Image    | `image/*` (PNG, JPG, GIF, WebP, etc.) |
| Subtitle | `.srt`, `.ass`, `.vtt` |
| JSON     | `.json` (for timeline/project import) |

### Upload Processing Pipeline

When files are selected, the Clip Library processes each file through a 3-stage pipeline:

1. **Stage 1 — File Registration (0-30%)**
   - A unique upload ID is generated
   - An `UploadItem` is created with `status: 'uploading'`
   - A `Clip` object is created with the appropriate type detected from MIME type
   - For images, a default duration of 5 seconds is assigned
   - For video/audio, an object URL is created for preview

2. **Stage 2 — Media Probing (30-60%)**
   - `probeMediaFile()` extracts metadata from the file
   - Video: duration, width, height
   - Audio: duration
   - Subtitle: cue count
   - On success: `clip.uploadStatus = 'ready'`
   - On failure: `clip.uploadStatus = 'error'` with error message

3. **Stage 3 — Completion (60-100%)**
   - Clip is added to `timelineStore.clips`
   - Upload item status set to `'success'`
   - Clip appears in the Clip Library list

### Upload Progress Display

The **Upload Progress List** (`UploadProgressList.vue`) shows all active and failed uploads:

- **File name** — Truncated if too long, with tooltip for full name
- **File size** — Formatted as B/KB/MB
- **Progress bar** — Visual indicator of upload progress percentage
- **Status label** — "Uploading", "Done", "Failed", or "Cancelled"
- **Cancel button** — Only shown for uploads in progress

Completed uploads can be cleared with the "Clear completed" button.

### Upload Limits and Constraints

| Constraint | Value | Notes |
|------------|-------|-------|
| Max file size | Browser-dependent | No hard limit enforced client-side |
| Concurrent uploads | Unlimited | Files processed sequentially per item |
| Image duration | 5 seconds | Fixed default for still images |
| Video probing | HTML5 `<video>` | Limited to browser-supported codecs |
| Audio probing | HTML5 `<audio>` | Limited to browser-supported codecs |
| Subtitle parsing | Client-side | SRT, ASS, VTT parsed in browser |

---

## Using the Demo Project

The **Demo Project** provides a fully populated timeline for users to explore the editor without uploading their own media.

### How to Load the Demo Project

1. Click "Try Demo Project" in the Empty Project Guide, OR
2. Click "Try Demo Project" in the Clip Library panel header

### Demo Project Contents

The demo project is created by `createDemoProject()` in `demoProjectFactory.ts`:

| Item Type | Count | Details |
|-----------|-------|---------|
| Video clips | 2 | "Demo Video - Intro" (10s), "Demo Video - Main" (15s) |
| Audio clips | 1 | "Demo Audio - Background" (30s) |
| Subtitle tracks | 1 | English, SRT format, 4 cues |
| Subtitle cues | 4 | "Welcome to Media Platform", "This is a demo project", "Try editing and exporting", "Enjoy creating!" |
| Effects | 1+ | `video.fade_in` with duration parameter |
| Transitions | 1+ | Crossfade (1s duration) between video clips |

### Demo Project Timeline Structure

After loading, the timeline contains:

```
Video 1 Track:
  [Demo Video - Intro 0-10s] [Demo Video - Main 10-25s]
                              ↕ crossfade transition

Audio 1 Track:
  [Demo Audio - Background 0-30s]

Text Track:
  (Subtitle cues rendered as overlays)
```

### Demo Clip Metadata

All demo clips are marked with `metadata.isDemo: 'true'` to distinguish them from user-uploaded content.

---

## Supported File Formats (Complete)

### Video Formats

| Format | Container | Common Codecs | Browser Support |
|--------|-----------|---------------|-----------------|
| MP4    | `.mp4`    | H.264, H.265  | Universal       |
| MOV    | `.mov`    | H.264, ProRes | Safari/Chrome   |
| AVI    | `.avi`    | Various       | Limited         |
| WebM   | `.webm`   | VP8, VP9      | Chrome/Firefox  |

### Audio Formats

| Format | Extension | Common Codecs | Browser Support |
|--------|-----------|---------------|-----------------|
| MP3    | `.mp3`    | MP3           | Universal       |
| WAV    | `.wav`    | PCM           | Universal       |
| AAC    | `.aac`    | AAC           | Universal       |
| OGG    | `.ogg`    | Vorbis        | Chrome/Firefox  |

### Image Formats

| Format | Extension | Notes |
|--------|-----------|-------|
| PNG    | `.png`    | Transparency supported |
| JPEG   | `.jpg`    | Lossy compression |
| GIF    | `.gif`    | Static (first frame) |
| WebP   | `.webp`   | Modern format |

### Subtitle Formats

| Format | Extension | Parser | Features |
|--------|-----------|--------|----------|
| SRT    | `.srt`    | `parseSRT()` | Basic timing + text |
| ASS/SSA | `.ass`   | `parseASS()` | Styles, positioning, effects |
| VTT    | `.vtt`    | `parseVTT()` | WebVTT with cues |

---

## Error Handling

| Error | Cause | Resolution |
|-------|-------|------------|
| "Metadata extraction failed" | File format not supported by browser | Re-encode file to a supported format |
| "Probe error" | Corrupted file or unsupported codec | Try a different file |
| Upload stuck at 30% | Media probing failing silently | Check browser console for errors |

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Enter` (on dropzone) | Open file browser |
| `Space` (on dropzone) | Open file browser |

---

## Component Reference

| Component | File | Purpose |
|-----------|------|---------|
| `EmptyProjectGuide` | `components/editor/EmptyProjectGuide.vue` | Empty state with upload/demo/subtitle buttons |
| `MediaUploadDropzone` | `components/upload/MediaUploadDropzone.vue` | Drag-and-drop file upload zone |
| `UploadProgressList` | `components/upload/UploadProgressList.vue` | Upload progress display |
| `ClipLibrary` | `components/clip-library/ClipLibrary.vue` | Clip management with search, filter, upload |
