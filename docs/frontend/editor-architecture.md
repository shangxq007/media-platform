# Editor Architecture — media-platform

**Date:** 2026-07-05
**Status:** PLANNED
**Authority:** FRONTEND-ARCH.0

---

## Core Principle

Editor core components are **platform-owned** and should NOT be bound to generic component libraries.

## Editor Components

### Timeline

| Component | Ownership | Notes |
|-----------|----------|-------|
| Timeline state | Platform | Zustand store |
| Track rendering | Platform | Custom virtual scroll |
| Clip rendering | Platform | Custom drag/resize |
| Selection model | Platform | Multi-select, range select |
| Undo/redo | Platform | Command pattern |
| Keyboard shortcuts | Platform | Custom key handler |

### Canvas

| Component | Ownership | Notes |
|-----------|----------|-------|
| Canvas overlay | Platform | Custom rendering |
| Text overlay | Platform | Caption positioning |
| Drag handles | Platform | Custom interaction |
| Safe area | Platform | Render contract driven |

### Caption Editor

| Component | Ownership | Notes |
|-----------|----------|-------|
| Segment editor | Platform | Word-level editing |
| Style inspector | Platform | CaptionStyleSchema binding |
| Font picker | Platform | FontManifest integration |
| Template preview | Platform | Remotion bridge |

### Inspector

| Component | Ownership | Notes |
|-----------|----------|-------|
| Property panel | Platform | Dynamic based on selection |
| Style editor | Platform | CaptionStyleSchema |
| Timeline properties | Platform | Track/clip properties |

## Interaction Model

- **Selection-driven:** Inspector shows properties of selected element
- **Command-based:** All mutations go through command pattern (undo/redo)
- **Contract-bound:** Editor state maps to RenderJob contract
- **Schema-validated:** All style changes validated against CaptionStyleSchema

## NOT Using Component Libraries For

- Timeline track/clip rendering
- Canvas overlay interactions
- Word-level caption editing
- Keyframe curve editing
- Frame-accurate time selection
- Drag-and-drop on timeline

## CAN Use Component Libraries For

- Dialogs (Radix)
- Popovers (Radix)
- Selects (Radix)
- Tabs (Radix)
- Tooltips (Radix)
- Menus (Radix)
- Buttons (design system)
- Forms (design system)
- Tables (design system)

---

*Document created by FRONTEND-ARCH.0*
