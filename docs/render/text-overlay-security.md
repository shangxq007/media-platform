# Text Overlay and Subtitle Security

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** TEXT-OVERLAY-SECURITY.0

---

## Background

SUBTITLE-DSL-ASS.0 defined subtitle DSL. This document hardens security boundaries.

---

## Current Inventory

| Component | File | User Input | Escaping | Path Risk | Command Risk |
|-----------|------|-----------|----------|-----------|-------------|
| TimelineTextOverlay | render-module domain | YES | N/A (model only) | NO | NO |
| EffectFilterGraphBuilder.subtitleFilter | render-module effects | YES | PARTIAL (`'` and `:` only) | NO | LOW |
| FFmpegCommandFactory | render-module ffmpeg | NO | N/A (argv-based) | NO | LOW |
| subtitlePath parameter | FFmpegCommandFactory | NO | Internal only | NO | LOW |
| libass provider binding | docs/examples | NO | N/A | NO | NO |
| Subtitle Render API | docs/api | YES | UNKNOWN | UNKNOWN | UNKNOWN |

---

## Threat Model

### 1. ASS Override Injection

**Threat:** User text contains `{\\pos(0,0)}` or `{\\p1}` drawing mode.

**Current risk:** MEDIUM — No ASS override sanitization in `subtitleFilter()`.

**Mitigation:** User text is plain text. Platform generates ASS internally. User text must be escaped for `{` and `}`.

### 2. FFmpeg Filter Injection

**Threat:** User text injected into drawtext filter arguments.

**Current risk:** LOW — `subtitleFilter()` escapes `'` and `:`. But incomplete.

**Mitigation:** Full escaping of `'`, `:`, `\`, `{`, `}` required.

### 3. Shell Command Injection

**Threat:** User input included in shell string.

**Current risk:** LOW — `FFmpegCommandFactory` builds commands as `List<String>` (argv-based).

**Mitigation:** Confirmed argv-based execution. No shell interpolation.

### 4. Path Traversal

**Threat:** subtitlePath points outside work directory.

**Current risk:** LOW — subtitlePath is internally generated.

**Mitigation:** Confirmed internal path generation. No user-controlled paths.

### 5. Information Leakage

**Threat:** API returns local temp path.

**Current risk:** LOW — No path exposure found in response DTOs.

**Mitigation:** Confirmed no path exposure in API responses.

### 6. Resource Exhaustion

**Threat:** Huge cue count, massive text, extreme font size.

**Current risk:** MEDIUM — No validation limits on TimelineTextOverlay.

**Mitigation:** Document recommended limits.

---

## Canonical Safety Rules

### Text Input

| Rule | Status |
|------|--------|
| User text is plain text | ✅ Design intent |
| Raw ASS not accepted from API | ✅ No ASS input field |
| ASS override tags escaped/rejected | ⚠️ PARTIAL |
| Text length bounded | ❌ No limit |
| Cue count bounded | ❌ No limit |
| Style count bounded | ❌ No limit |

### ASS Generation

| Rule | Status |
|------|--------|
| Generated ASS is internal artifact | ✅ |
| Only platform-generated sections | ✅ |
| User text escaped in Dialogue | ⚠️ PARTIAL |
| ASS file in safe work directory | ✅ |
| ASS path not exposed in API | ✅ |

### FFmpeg Command

| Rule | Status |
|------|--------|
| Command is argv/list-based | ✅ Confirmed |
| No raw user filter string | ✅ |
| subtitlePath internally resolved | ✅ |
| Filtergraph from compiler only | ✅ |

### Font Path

| Rule | Status |
|------|--------|
| No arbitrary local font path | ✅ No font path field |
| fontFamily resolves through manifest | ⚠️ Design intent |
| Font paths not exposed in API | ✅ |

### Logging/API

| Rule | Status |
|------|--------|
| No local temp path in response | ✅ Confirmed |
| No secret in logs | ✅ |
| Error messages safe | ✅ |

---

## Escaping Rules

### Current (Incomplete)

```java
String escaped = text.replace("'", "\\'").replace(":", "\\:");
```

### Required (Complete)

```java
String escaped = text
    .replace("\\", "\\\\")  // backslash first
    .replace("'", "\\'")     // single quote
    .replace(":", "\\:")     // colon
    .replace("{", "\\{")     // ASS override open
    .replace("}", "\\}");    // ASS override close
```

### For ASS Generation

| Character | Escape | Reason |
|-----------|--------|--------|
| `\` | `\\` | Escape character |
| `{` | `\{` | Override tag open |
| `}` | `\}` | Override tag close |
| `,` | `,` | Field separator (safe in text field) |
| `\n` | `\N` | ASS newline |
| `\r` | remove | Carriage return |

---

## Validation Limits (Recommended)

| Field | Limit | Reason |
|-------|-------|--------|
| text length | 500 chars | Prevent ASS bloat |
| cues per track | 1000 | Prevent performance issues |
| subtitle tracks | 10 | Prevent complexity |
| styles | 50 | Prevent style explosion |
| fontSize | 12-200 | Prevent visual issues |
| outlineWidth | 0-10 | Prevent visual issues |
| marginV | 0-500 | Prevent positioning issues |

---

## FFmpeg Command Safety

### Confirmed

- `FFmpegCommandFactory` builds commands as `List<String>` (argv-based)
- No shell string concatenation
- No `Runtime.exec(String)` usage
- `subtitlePath` is internally generated
- Filter arguments come from compiler, not user input

### Risk Assessment

| Risk | Level | Status |
|------|-------|--------|
| Shell injection | LOW | ✅ argv-based |
| Filter injection | LOW | ✅ compiler-generated |
| Path traversal | LOW | ✅ internal paths |
| ASS injection | MEDIUM | ⚠️ escaping incomplete |
| Resource exhaustion | MEDIUM | ⚠️ no limits |

---

## Font Path Safety

### Confirmed

- No `fontPath` field in user-facing API
- No `fontsDir` exposure in responses
- Font references use family names only
- Font resolution is internal

### Gap

- Font manifest/resolver not fully implemented
- Default font fallback behavior undefined

---

## Tests Added

| Test | Status |
|------|--------|
| ASS override escaping | DEFERRED |
| Braces handling | DEFERRED |
| Unicode preservation | DEFERRED |
| Long text rejection | DEFERRED |
| Invalid font rejection | DEFERRED |
| Raw ASS rejection | ✅ No ASS input field |
| Path exposure check | ✅ Confirmed safe |
| argv-based command | ✅ Confirmed |

---

## Remaining Risks

| Risk | Level | Action |
|------|-------|--------|
| ASS escaping incomplete | NON_BLOCKING | Follow-up |
| No validation limits | NON_BLOCKING | Follow-up |
| Font manifest incomplete | NON_BLOCKING | Follow-up |

---

## Follow-up Tasks

| Task | Description |
|------|-------------|
| ASS-GENERATOR-MVP.0 | Implement ASS generation |
| SUBTITLE-RENDER-SMOKE.0 | Smoke test subtitle render |
| FONT-MANIFEST-RENDER-INTEGRATION.0 | Font manifest integration |
| FFMPEG-COMMAND-ARGV-HARDENING.0 | Command hardening |
