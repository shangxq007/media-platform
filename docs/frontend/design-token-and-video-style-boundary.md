# Design Token and Video Style Boundary — media-platform

**Date:** 2026-07-05
**Status:** PLANNED
**Authority:** FRONTEND-ARCH.0

---

## Three-Layer Model

### Layer 1: Web Product UI Tokens

**Purpose:** Style the web application (buttons, forms, panels, settings, jobs, admin)

**Examples:**
```css
--color-bg: #0d1117
--color-surface: #161b22
--color-border: #30363d
--color-text: #e6edf3
--color-accent: #58a6ff
--spacing-sm: 8px
--spacing-md: 16px
--radius-sm: 4px
--radius-md: 8px
```

**Managed by:** StyleX / Astryx / Tailwind / CSS Modules / Vanilla Extract / Panda CSS

**Scope:** Web UI only

---

### Layer 2: Editor Interaction Tokens

**Purpose:** Style editor-specific interactions (timeline, canvas, caption editor)

**Examples:**
```css
--editor-track-height: 48px
--editor-clip-min-width: 20px
--editor-playhead-color: #ff0000
--editor-selection-color: rgba(88, 166, 255, 0.3)
--editor-snap-threshold: 5px
```

**Managed by:** Platform-owned design tokens

**Scope:** Editor UI only

---

### Layer 3: Video Render Styles

**Purpose:** Define how video output looks (captions, overlays, effects)

**NOT Web UI CSS.** These are platform-owned schemas.

**Examples:**
```typescript
// CaptionStyleSchema
{
  font: {
    family: "Noto Sans",
    size: 48,
    color: "#FFFFFF",
    weight: 700,
    outlineWidth: 2,
    outlineColor: "#000000"
  },
  placement: "BOTTOM_CENTER",
  maxLines: 2,
  lineHeight: 1.2
}

// TextStyleToken
{
  fontFamily: "Noto Sans CJK SC",
  fontSize: 48,
  fontWeight: 700,
  color: "#FFFFFF",
  strokeColor: "#000000",
  strokeWidth: 2,
  shadowColor: "rgba(0,0,0,0.5)",
  shadowBlur: 4,
  shadowOffsetX: 2,
  shadowOffsetY: 2
}
```

**Managed by:** Platform schemas (CaptionStyleSchema, TextStyleToken, FontManifest, RenderJob props)

**Consumed by:** Backend render pipeline, Remotion preview, libass, FFmpeg

---

## Critical Boundary

**StyleX / Astryx / Tailwind are Web UI styling solutions.**

**They are NOT video render style DSLs.**

Video render styles must use platform-owned schemas that:
- Map to backend render contracts
- Work with libass/FFmpeg
- Work with Remotion compositions
- Support font manifest and glyph coverage
- Support multi-provider rendering

---

## Token Flow

```
Design Tokens (Layer 1)
  ↓
StyleX/Astryx/Tailwind
  ↓
Web UI Components

Editor Tokens (Layer 2)
  ↓
Platform Design System
  ↓
Editor Components

Video Styles (Layer 3)
  ↓
CaptionStyleSchema / TextStyleToken
  ↓
Backend Render Pipeline
  ↓
Remotion Preview
  ↓
Final Video Output
```

---

*Document created by FRONTEND-ARCH.0*
