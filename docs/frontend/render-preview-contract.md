# Render Preview Contract — media-platform

**Date:** 2026-07-05
**Status:** PLANNED
**Authority:** FRONTEND-ARCH.0

---

## Overview

Remotion preview is a **render preview bridge**, not a regular UI component library.

## Contract Flow

```
Frontend Editor State
  ↓
TimelineSpec (platform schema)
  ↓
CaptionStyleSchema (platform schema)
  ↓
FontManifest (platform schema)
  ↓
RenderJob assembly
  ↓
Preview API request
  ↓
Backend preview render
  ↓
PreviewProps → Remotion Player
```

## Key Schemas

### CaptionStyleSchema

```typescript
interface CaptionStyleSchema {
  font: {
    family: string;
    size: number;
    color: string;
    weight: number;
    outlineWidth: number;
    outlineColor: string;
  };
  placement: 'BOTTOM_CENTER' | 'TOP_CENTER' | 'CENTER';
  maxLines: number;
  lineHeight: number;
}
```

### FontManifest

```typescript
interface FontManifest {
  fonts: FontEntry[];
  missingGlyphs: string[];
  fallbackChain: string[];
}

interface FontEntry {
  family: string;
  subsets: FontSubset[];
  coverage: string[];
}

interface FontSubset {
  url: string;
  format: string;
  unicodeRange: string;
}
```

### PreviewProps (Remotion)

```typescript
interface PreviewProps {
  timeline: TimelineSpec;
  captionStyle: CaptionStyleSchema;
  fontManifest: FontManifest;
  assets: AssetReference[];
}
```

## Boundary Rules

1. **Remotion receives render-facing contracts**, not UI styling details
2. **Visual consistency** comes from render contracts, not Web UI CSS
3. **StyleX/Astryx/Tailwind** style the editor UI; render output uses platform schemas
4. **Font rendering** uses FontManifest, not CSS font-family
5. **Caption style** uses CaptionStyleSchema, not CSS properties

## NOT Remotion's Job

- Styling the editor UI
- Managing editor state
- Handling user interactions
- Providing design system components

## IS Remotion's Job

- Rendering video preview
- Consuming PreviewProps
- Playing back timeline
- Showing caption overlay
- Previewing font rendering

---

*Document created by FRONTEND-ARCH.0*
