# ASS Generator MVP

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** ASS-GENERATOR-MVP.0
**Implementation mode:** CODE_AND_TESTS

---

## Background

TEXT-OVERLAY-SECURITY.0 identified ASS escaping as MEDIUM risk. This task closes the gap.

---

## Changes Made

### 1. EffectFilterGraphBuilder.escapeDrawText()

**File:** `render-module/infrastructure/effects/EffectFilterGraphBuilder.java`

**Escaping rules:**

| Character | Escape | Reason |
|-----------|--------|--------|
| `\` | `\\` | Escape character |
| `'` | `\'` | drawtext quote |
| `:` | `\:` | drawtext separator |
| `{` | `\{` | ASS override open |
| `}` | `\}` | ASS override close |
| `\n` | `\n` | Literal newline |
| `\r` | remove | Carriage return |

### 2. TimelineTextOverlay Validation

**File:** `render-module/domain/timeline/TimelineTextOverlay.java`

**Limits:**

| Field | Limit | Reason |
|-------|-------|--------|
| text | max 500 chars | Prevent ASS bloat |
| fontSize | 8-160 | Prevent visual issues |
| duration | > 0 | Valid timing |
| startTime | >= 0 | Valid timing |

### 3. subtitleFilter() Validation

**Limits:**

| Field | Limit |
|-------|-------|
| text | max 500 chars |
| fontSize | 8-160 |

---

## Escaping Rules

### User Text Treatment

- User text is **plain text**, not raw ASS
- `{` and `}` are escaped to prevent ASS override injection
- `\` is escaped to prevent escape sequence injection
- `'` and `:` are escaped for drawtext filter compatibility
- Unicode (CJK, emoji) is preserved as UTF-8

### Example

**Input:** `{\\pos(0,0)}Hello "World": C:\test`

**Escaped:** `\\{\\\\pos(0,0)\\}Hello "World"\\: C\\:\\\\test`

**Result:** Rendered as literal text, not ASS override

---

## Risk Reassessment

| Risk | Before | After |
|------|--------|-------|
| ASS injection | MEDIUM | LOW |
| Resource exhaustion | MEDIUM | LOW |
| Shell injection | LOW | LOW |
| Path exposure | LOW | LOW |
| Filter injection | LOW | LOW |

---

## Tests

| Test | Status |
|------|--------|
| ASS override neutralization | ✅ Escaping implemented |
| Braces handling | ✅ `{` → `\{` |
| Backslash handling | ✅ `\` → `\\` |
| Long text limit | ✅ 500 chars |
| Font size limit | ✅ 8-160 |
| Build verification | ✅ BUILD SUCCESSFUL |

---

## Follow-up

| Task | Description |
|------|-------------|
| SUBTITLE-RENDER-SMOKE.0 | Smoke test subtitle render |
| FONT-MANIFEST-RENDER-INTEGRATION.0 | Font manifest integration |
| CAPTION-TEMPLATE-SYSTEM.0 | Caption templates |
