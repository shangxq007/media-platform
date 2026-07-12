# Styling Technology Decision — media-platform

**Date:** 2026-07-05
**Status:** EVALUATING
**Authority:** FRONTEND-ARCH.0

---

## Context

The new frontend needs a styling solution for:
1. Web product UI (settings, jobs, admin, asset list)
2. Editor interaction UI (timeline, canvas, caption editor)
3. Video render styles (NOT Web UI CSS)

This document evaluates candidates for Layers 1 and 2 only. Layer 3 uses platform-owned schemas.

---

## Candidates

### 1. StyleX

| Aspect | Assessment |
|--------|------------|
| Type safety | ✅ Typed styles |
| Atomic CSS | ✅ Low specificity conflicts |
| Design tokens | ✅ First-class support |
| React integration | ✅ Excellent |
| Agent-friendly | ✅ Predictable output |
| Build integration | ⚠️ Needs Vite plugin |
| Learning curve | ⚠️ Moderate |
| Ecosystem | ⚠️ Newer |

**Recommendation:** Strong candidate for design-system/ui/admin/settings/jobs

**Scope:** Web product UI (Layer 1), possibly editor UI (Layer 2) after POC

**Risk:** Vite/React 19 compatibility needs verification

---

### 2. Astryx

| Aspect | Assessment |
|--------|------------|
| Internal tools | ✅ Designed for admin/ops |
| Rapid development | ✅ Fast UI building |
| Component library | ✅ Pre-built components |
| Editor suitability | ❌ Not for high-interaction editors |
| Ecosystem | ⚠️ Newer, less stable |

**Recommendation:** Candidate for admin/ops/settings/dashboard only

**Scope:** Settings, admin console, job dashboard, provider matrix

**NOT suitable for:** Timeline, canvas, caption editor, frame-accurate interactions

---

### 3. Tailwind CSS

| Aspect | Assessment |
|--------|------------|
| Ecosystem | ✅ Mature, large |
| Learning curve | ✅ Low |
| Component libraries | ✅ Many available |
| Design tokens | ⚠️ Weaker type constraints |
| Agent generation | ⚠️ Can produce messy classes |
| Editor suitability | ⚠️ Complex states hard to maintain |

**Recommendation:** Viable for rapid prototyping and simple UIs

**Risk:** Complex editor states may become hard to maintain

---

### 4. CSS Modules

| Aspect | Assessment |
|--------|------------|
| Simplicity | ✅ Very simple |
| Build cost | ✅ Low |
| Vite compatibility | ✅ Native |
| Design tokens | ⚠️ Requires manual discipline |
| Large app consistency | ⚠️ Needs strict conventions |

**Recommendation:** Conservative fallback option

**Scope:** Local component styles, small-medium UIs

---

### 5. Vanilla Extract

| Aspect | Assessment |
|--------|------------|
| Type safety | ✅ Full TypeScript |
| Static CSS | ✅ Zero runtime |
| Design tokens | ✅ First-class |
| Boilerplate | ⚠️ More verbose |
| Ecosystem | ✅ Growing |

**Recommendation:** Strong candidate for token-first approach

**Scope:** Design system, component library, typed styles

---

### 6. Panda CSS

| Aspect | Assessment |
|--------|------------|
| Atomic CSS | ✅ Built-in |
| Design tokens | ✅ Strong |
| Recipes | ✅ Component variants |
| Type safety | ✅ Good |
| Ecosystem | ⚠️ Newer |

**Recommendation:** Alternative to Vanilla Extract

**Scope:** Design system, atomic styles, token-first

---

### 7. Radix / shadcn-style

| Aspect | Assessment |
|--------|------------|
| Headless primitives | ✅ Excellent |
| Accessibility | ✅ Built-in |
| Customization | ✅ Full control |
| Editor core | ❌ Not for timeline/canvas |

**Recommendation:** Use for dialog, popover, select, tabs, tooltip, menu

**NOT for:** Timeline, canvas, caption editor core interactions

---

## Summary Matrix

| Technology | Web UI | Editor UI | Tokens | Agent-friendly | Recommendation |
|------------|--------|-----------|--------|----------------|----------------|
| StyleX | ✅ | ⚠️ POC | ✅ | ✅ | Primary candidate |
| Astryx | ✅ | ❌ | ⚠️ | ✅ | Admin/ops only |
| Tailwind | ✅ | ⚠️ | ⚠️ | ⚠️ | Rapid proto |
| CSS Modules | ✅ | ⚠️ | ❌ | ✅ | Fallback |
| Vanilla Extract | ✅ | ⚠️ | ✅ | ✅ | Token-first |
| Panda CSS | ✅ | ⚠️ | ✅ | ✅ | Alternative |
| Radix/shadcn | ✅ | ❌ | N/A | ✅ | Primitives |

---

## Preliminary Recommendation

**Option A: StyleX-first Design System**
- StyleX for design-system/ui/admin/settings/jobs
- Radix primitives for headless interactions
- Custom editor components for timeline/canvas/caption
- Platform-owned CaptionStyleSchema for render styles

**Option B: Tailwind + Radix / shadcn-style**
- Tailwind for rapid UI
- Radix primitives
- Custom editor components
- Platform-owned render style schema

**Option C: CSS Modules + Design Tokens**
- CSS Modules
- CSS variables tokens
- Radix primitives
- Custom editor components

**Option D: Vanilla Extract / Panda CSS**
- Typed styling
- Design tokens
- Custom editor components

---

## Decision Status

**NOT YET DECIDED.** Requires scoped POC before adoption.

Recommended next step: **FRONTEND-STYLE-POC.0**

---

*Document created by FRONTEND-ARCH.0*
