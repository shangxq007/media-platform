# Manual Demo Script

> **Purpose:** End-to-end manual demonstration script for human reviewers.  
> **Environment:** Local development (Docker + H2 in-memory DB)  
> **Estimated Time:** 30-45 minutes

---

## Prerequisites

```bash
# 1. Start the backend
cd media-platform
./gradlew :platform-app:bootRun &
# Wait for "Started PlatformApp" in logs

# 2. Start the frontend (in another terminal)
cd media-platform/frontend
npm run dev &
# Open http://localhost:3000

# 3. Verify health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

---

## Step 1: Create Tenant / User / Project

### 1.1 Create a Tenant
```bash
curl -X POST http://localhost:8080/api/v1/identity/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "Demo Tenant", "tier": "TEAM"}'
```
**Expected:** `201 Created` with tenant ID.

### 1.2 Create a User
```bash
curl -X POST http://localhost:8080/api/v1/identity/users \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@example.com", "role": "EDITOR"}'
```
**Expected:** `201 Created` with user ID.

### 1.3 Create a Project
```bash
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"name": "Demo Project", "description": "E2E demo"}'
```
**Expected:** `201 Created` with project ID.

**Frontend:** Open `http://localhost:3000` → Verify project appears in sidebar.

---

## Step 2: Edit Timeline

### 2.1 Navigate to Editor
- Open `http://localhost:3000`
- Select the project created above

### 2.2 Verify Timeline Editor
- [ ] Video track visible
- [ ] Audio track visible
- [ ] Text track visible
- [ ] Playhead can be dragged

### 2.3 Add a Clip (via API for demo)
```bash
curl -X POST http://localhost:8080/api/v1/timeline/tracks/{trackId}/clips \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"type": "video", "start": 0, "duration": 10, "source": "demo.mp4"}'
```
**Frontend:** Verify clip appears on timeline.

---

## Step 3: Add Effect Pack

### 3.1 List Effect Packs
```bash
curl http://localhost:8080/api/v1/effects/packs \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** List of available effect packs (basic, pro, team).

### 3.2 Apply Effect to Clip
```bash
curl -X POST http://localhost:8080/api/v1/effects/apply \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"clipId": "clip-1", "effectId": "blur", "parameters": {"radius": 5}}'
```
**Frontend:** Open Effects Panel → Verify effect applied.

---

## Step 4: Upload Subtitle

### 4.1 Upload SRT File
```bash
curl -X POST http://localhost:8080/api/v1/subtitles/upload \
  -H "X-Tenant-ID: tenant-1" \
  -F "file=@demo.srt" \
  -F "language=en"
```
**Expected:** `201 Created` with subtitle track ID.

**Frontend:** Open Subtitle Timeline → Verify cues visible.

---

## Step 5: Select Font

### 5.1 List Available Fonts
```bash
curl http://localhost:8080/api/v1/subtitles/fonts \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** List of fonts with metadata.

### 5.2 Assign Font to Subtitle Track
```bash
curl -X POST http://localhost:8080/api/v1/subtitles/tracks/{trackId}/font \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"fontId": "font-1"}'
```
**Frontend:** Verify font applied in subtitle preview.

---

## Step 6: Set Multi-Language Subtitle

### 6.1 Add Second Language
```bash
curl -X POST http://localhost:8080/api/v1/subtitles/upload \
  -H "X-Tenant-ID: tenant-1" \
  -F "file=@demo_zh.srt" \
  -F "language=zh"
```
**Frontend:** Verify both languages in subtitle timeline.

---

## Step 7: Select Export Preset

### 7.1 List Available Presets
```bash
curl http://localhost:8080/api/v1/render/presets \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** List of presets filtered by tier.

### 7.2 Validate Export
```bash
curl -X POST http://localhost:8080/api/v1/render/export/validate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"preset": "default_1080p", "outputFormat": "mp4"}'
```
**Expected:** Validation result with estimated cost.

**Frontend:** Open Export Panel → Select preset → Verify estimated cost displayed.

---

## Step 8: Select Worker

### 8.1 Check Worker Status
```bash
curl http://localhost:8080/api/v1/remote-worker/workers \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** List of registered workers.

**Frontend:** Open Export Panel → Switch between Local/Remote worker.

---

## Step 9: Submit RenderJob

### 9.1 Submit Job
```bash
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{
    "projectId": "project-1",
    "format": "mp4",
    "resolution": "1080p",
    "profile": "default_1080p",
    "audioTrack": "all",
    "frameRate": 30,
    "encoder": "h264"
  }'
```
**Expected:** `202 Accepted` with job ID.

### 9.2 Check Job Status
```bash
curl http://localhost:8080/api/v1/render/jobs/{jobId} \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** Job status (QUEUED, PROCESSING, COMPLETED, or FAILED).

**Frontend:** Verify job status updates in Export Panel.

---

## Step 10: Verify Artifact Output

### 10.1 List Artifacts
```bash
curl http://localhost:8080/api/v1/artifacts \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** List of artifacts for the completed job.

---

## Step 11: View Notification / Audit

### 11.1 List Notifications
```bash
curl http://localhost:8080/api/v1/notifications \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** Render completion notification.

### 11.2 List Audit Records
```bash
curl http://localhost:8080/api/v1/audit/compliance/records \
  -H "X-Tenant-ID: tenant-1"
```
**Expected:** Audit trail with render job events.

---

## Step 12: Trigger Error and Verify ErrorCode

### 12.1 Submit Invalid Request
```bash
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"format": "invalid"}'
```
**Expected:** `400 Bad Request` with `RENDER-400-001` error code.

### 12.2 Trigger Budget Exceeded
```bash
# First, set a low budget
curl -X PUT http://localhost:8080/api/v1/billing/tenants/tenant-1/budget \
  -H "Content-Type: application/json" \
  -d '{"budgetLimit": 0.01}'

# Then submit an expensive render
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"profile": "team_4k", "format": "mp4"}'
```
**Expected:** `402 Payment Required` with `COST-402-001` error code.

**Frontend:** Verify error message displayed in i18n.

---

## Step 13: Trigger Frontend Feedback

### 13.1 Click Feedback Button
- Click the blue "Feedback" button in bottom-right corner
- Fill in: Type=Bug, Severity=Medium, Title="Test feedback", Description="Demo"
- Click Submit

**Expected:** "✓ Feedback submitted successfully!" message.

### 13.2 Verify Monitoring Status
- Click "Show" next to Monitoring Status
- Verify Sentry/OpenReplay status indicators

---

## Step 14: Trigger Frontend Exception

### 14.1 Open Browser DevTools Console
```javascript
// Trigger a Vue error
window.__vue_app__.config.errorHandler(new Error("Test exception"), null, "manual-test")
```
**Expected:** Error captured by Sentry (if DSN configured).

---

## Step 15: View Prompt Management UI

### 15.1 Navigate to Prompts
- Click "Prompts" link in header
- Verify template list loads

### 15.2 Create a Template
```bash
curl -X POST http://localhost:8080/api/v1/prompts/templates \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"name": "Demo Prompt", "category": "test", "schemaVersion": "1.0.0"}'
```
**Expected:** `201 Created`.

**Frontend:** Verify template appears in list.

---

## Step 16: Execute Prompt Render Preview

### 16.1 Select Template
- Click on the template created above
- Go to "Render" tab

### 16.2 Enter Variables
```json
{"name": "World", "language": "en"}
```

### 16.3 Click Preview
**Expected:** Rendered output with variables substituted.

---

## Step 17: Verify High-Risk Prompt BLOCK/REQUIRE_REVIEW

### 17.1 Analyze Risky Content
```bash
curl -X POST http://localhost:8080/api/v1/prompts/risk/analyze \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"content": "rm -rf / && password: secret123", "category": "test"}'
```
**Expected:** Risk level CRITICAL or HIGH, action BLOCK or REQUIRE_REVIEW.

**Frontend:** Go to Risk tab → Enter risky content → Click Analyze → Verify risk badge.

---

---

## Step 18: Test Empty Project Guide (Prompt 62)

### 18.1 Verify Empty State
- Open editor with no clips
- **Expected:** Empty Project Guide renders with 🎬 icon, "Start Your Project" heading
- **Expected:** Three buttons visible: Upload Files, Try Demo Project, Import Subtitle
- **Expected:** "Supports: MP4, MOV, AVI, MP3, WAV, SRT, ASS, VTT" text at bottom

### 18.2 Test Upload Button
- Click "Upload Files" button
- **Expected:** Native file browser opens
- Select a test video file
- **Expected:** File appears in Clip Library with upload progress
- **Expected:** After probing, clip shows duration and resolution

### 18.3 Test Demo Project
- Click "Try Demo Project"
- **Expected:** Clip Library populates with 3 clips (2 video, 1 audio)
- **Expected:** Timeline shows Video 1 track with 2 clips, Audio 1 track with 1 clip
- **Expected:** Subtitles panel shows English track with 4 cues
- **Expected:** Effects panel shows fade_in effect on first video clip

### 18.4 Test Import Subtitle
- Click "Import Subtitle"
- **Expected:** Native file browser opens filtered for subtitle files
- Select an SRT file
- **Expected:** Subtitle track appears in Subtitles Panel

---

## Step 19: Test Upload Dropzone (Prompt 62)

### 19.1 Drag and Drop
- Drag a file from OS file manager to the dropzone
- **Expected:** Dropzone highlights (border + background change)
- Release file
- **Expected:** Upload progress appears, clip is processed

### 19.2 Click to Browse
- Click the dropzone area
- **Expected:** Native file browser opens

### 19.3 Upload Progress
- Upload a large file
- **Expected:** Progress bar advances from 0% to 100%
- **Expected:** File name and size displayed
- **Expected:** Cancel button (✕) visible during upload
- **Expected:** "Clear completed" button appears after upload finishes

---

## Step 20: Test Render Job Status (Prompt 62)

### 20.1 Submit Render Job
- Load demo project
- Open Export Panel → Select preset → Click "Export Video"
- **Expected:** Render job ID displayed in Export Panel
- **Expected:** Status badge shows "running" or "queued"
- **Expected:** Progress bar visible

### 20.2 Test Failed State
- Trigger a render failure (e.g., invalid preset)
- **Expected:** "Render Failed" message displayed
- **Expected:** Error code shown (e.g., RENDER-500-001)
- **Expected:** Retry button visible
- **Expected:** Copy diagnostic info button visible

### 20.3 Test Cancel
- Submit a render job
- Click "Cancel" button
- **Expected:** Confirmation dialog appears
- Click "OK"
- **Expected:** Status changes to "cancelled"

---

## Step 21: Test Artifact Result (Prompt 62)

### 21.1 View Completed Artifact
- Wait for render job to complete (or use demo mode)
- **Expected:** Artifact name displayed
- **Expected:** Format badge (MP4) shown
- **Expected:** File size formatted (e.g., "15.0 MB")
- **Expected:** Duration formatted (e.g., "1:30")
- **Expected:** Resolution shown (e.g., "1920×1080")
- **Expected:** Provider name displayed
- **Expected:** "Completed" badge shown

### 21.2 Test Preview Modal
- Click "Preview" button on artifact
- **Expected:** Modal opens with video/audio/image element
- Click close button
- **Expected:** Modal closes

### 21.3 Test Download
- Click "Download" button
- **Expected:** New tab opens with output URL

---

## Step 22: Test ErrorState Component (Prompt 62)

### 22.1 Default Error Display
- Trigger an error state (e.g., disconnect backend)
- **Expected:** "Something went wrong" title displayed
- **Expected:** "An unexpected error occurred. Please try again." description
- **Expected:** Retry button visible
- **Expected:** Dismiss button visible

### 22.2 Error Code Display
- Trigger error with error code
- **Expected:** Error code displayed in monospace font

### 22.3 Diagnostic ID
- Trigger error with diagnostic ID
- **Expected:** Diagnostic ID displayed with Copy button
- Click Copy
- **Expected:** "✓ Copied!" feedback

### 22.4 Admin Debug
- Enable admin debug mode
- Click "Show Debug Info"
- **Expected:** JSON dump of error details displayed

---

## Step 23: Test i18n Error Messages (Prompt 62)

### 23.1 English Messages
- Set locale to English
- Trigger RENDER-500-001 error
- **Expected:** "Render execution failed" displayed

### 23.2 Chinese Messages
- Set locale to Chinese
- Trigger RENDER-500-001 error
- **Expected:** "渲染执行失败" displayed

### 23.3 Fallback
- Trigger unknown error code
- **Expected:** Error code itself displayed as fallback

---

## Known Limitations (Prompt 62)

| Limitation | Description | Planned Fix |
|------------|-------------|-------------|
| Demo project uses stubs | Demo clips have no actual media files | Real demo media in future |
| Render is stubbed | Backend render returns mock artifacts | Real render pipeline integration |
| Preview limited to browser formats | Only MP4/MP3/PNG/JPG previewable | Transcode for preview |
| No batch export | One export at a time | Queue system planned |
| Upload size not validated | No client-side size check | Add size validation |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Backend won't start | Check port 8080 is free: `lsof -i :8080` |
| Frontend won't start | Check port 3000 is free: `lsof -i :3000` |
| CORS errors | Verify proxy config in vite.config.ts |
| 404 on API calls | Verify backend is running on port 8080 |
| Test failures | Run `./gradlew clean test` to reset |
| Upload stuck at 30% | Media probing may fail for unsupported formats |
| Preview not available | Format not supported by browser |
| render job stuck QUEUED | Backend render provider may not be running |
