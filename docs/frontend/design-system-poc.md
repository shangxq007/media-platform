# Frontend Design System POC

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** FRONTEND-DESIGN-SYSTEM-POC.0

---

## Current Frontend Baseline

| Technology | Status |
|------------|--------|
| React 19 | ✅ BASELINE |
| TanStack Router | ✅ BASELINE |
| TanStack Query | ✅ BASELINE |
| Zustand | ✅ BASELINE |
| Zod | ✅ BASELINE |
| TypeScript | ✅ BASELINE |

---

## Current Styling Discovery

**Important finding:** Tailwind CSS is already in `package.json` and used in existing components!

| Component | Styling Approach |
|-----------|------------------|
| RootLayout | Tailwind classes |
| ArtifactPreview | Tailwind classes |
| TimelineGitConsolePage | Inline styles |

**Current state:** Mixed — Tailwind exists but new dev console uses inline styles.

---

## Evaluation Criteria

| Criterion | Weight | Description |
|-----------|--------|-------------|
| React 19 compatibility | HIGH | Must work with React 19 |
| TypeScript experience | HIGH | Type-safe styles |
| Agent friendliness | HIGH | Easy for AI to generate |
| Build complexity | MEDIUM | Setup/maintenance cost |
| Component ergonomics | MEDIUM | Tables/cards/badges/tabs |
| Bundle size | LOW | Impact on build |
| Learning curve | LOW | Adoption friction |

---

## Candidate Evaluation

### 1. Existing Local Styling (Inline Styles)

| Criterion | Score | Notes |
|-----------|-------|-------|
| React 19 | 5 | Native support |
| TypeScript | 4 | Type-safe objects |
| Agent friendly | 4 | Easy to generate |
| Build complexity | 5 | Zero setup |
| Components | 3 | Manual tables/cards |
| Bundle size | 5 | Zero overhead |
| Learning curve | 5 | No learning |

**Status:** CURRENT_BASELINE

**Strengths:**
- Zero dependencies
- Already working in TimelineGitConsolePage
- Type-safe inline objects
- Easy for agents to generate

**Weaknesses:**
- No design tokens
- Inconsistent spacing/colors
- Manual table/card styling
- Duplication across components

---

### 2. Tailwind CSS

| Criterion | Score | Notes |
|-----------|-------|-------|
| React 19 | 5 | Full support |
| TypeScript | 3 | Class strings |
| Agent friendly | 5 | Very familiar |
| Build complexity | 4 | Config exists |
| Components | 4 | Utility classes |
| Bundle size | 4 | Purged |
| Learning curve | 4 | Well documented |

**Status:** ALREADY_IN_PROJECT

**Strengths:**
- Already in package.json
- Already used in RootLayout/ArtifactPreview
- Agent-friendly class names
- Large ecosystem
- Fast prototyping

**Weaknesses:**
- Class name sprawl risk
- Not type-safe
- Could confuse Web UI with render styles

**Note:** Tailwind is already available. New dev console components chose inline styles instead.

---

### 3. StyleX

| Criterion | Score | Notes |
|-----------|-------|-------|
| React 19 | 5 | Full support |
| TypeScript | 5 | Fully typed |
| Agent friendly | 3 | Learning curve |
| Build complexity | 2 | Build plugin required |
| Components | 3 | Style objects |
| Bundle size | 5 | Atomic CSS |
| Learning curve | 2 | New paradigm |

**Status:** SCOPED_POC_CANDIDATE

**Strengths:**
- Fully type-safe styles
- Atomic CSS output
- Compile-time optimization
- Facebook/Meta proven

**Weaknesses:**
- Requires build plugin
- New paradigm for agents
- Style objects, not components
- No pre-built components

---

### 4. Astryx

| Criterion | Score | Notes |
|-----------|-------|-------|
| React 19 | 4 | Should work |
| TypeScript | 4 | Typed props |
| Agent friendly | 4 | Component API |
| Build complexity | 3 | New dependency |
| Components | 5 | Pre-built |
| Bundle size | 3 | Component library |
| Learning curve | 3 | New library |

**Status:** SCOPED_POC_CANDIDATE

**Strengths:**
- Pre-built React components
- Component API (not style objects)
- Good for tables/cards/badges
- Design system out of box

**Weaknesses:**
- Newer/less proven
- May not fit all needs
- Dependency risk
- Less agent training data

---

## Score Matrix

| Criterion | Local | Tailwind | StyleX | Astryx |
|-----------|-------|----------|--------|--------|
| React 19 | 5 | 5 | 5 | 4 |
| TypeScript | 4 | 3 | 5 | 4 |
| Agent friendly | 4 | 5 | 3 | 4 |
| Build complexity | 5 | 4 | 2 | 3 |
| Components | 3 | 4 | 3 | 5 |
| Bundle size | 5 | 4 | 5 | 3 |
| Learning curve | 5 | 4 | 2 | 3 |
| **Total** | **31** | **29** | **25** | **26** |

---

## Decision

### Current Baseline
**Existing local styling / inline styles**

Reason: Already working, zero dependencies, type-safe, agent-friendly.

### Tailwind Status
**ALREADY_IN_PROJECT but NOT SELECTED for new dev console**

Reason: Available but TimelineGitConsolePage chose inline styles. Tailwind used in other components.

### StyleX Status
**SCOPED_POC_CANDIDATE**

Reason: Type-safe but requires build plugin. Good for future if type safety becomes priority.

### Astryx Status
**SCOPED_POC_CANDIDATE**

Reason: Pre-built components good for admin console. Newer but promising.

---

## Recommendation

### Short-term (current)
Keep **inline styles** for /dev/* console.
- Already working
- Zero setup
- Fast iteration

### Medium-term (next)
Use **Tailwind** for /admin/* console.
- Already in project
- Agent-friendly
- Fast prototyping

### Long-term (future)
Evaluate **Astryx** for /app/* user workspace.
- Pre-built components
- Design system out of box
- Good for user-facing UI

### StyleX
Defer unless type safety becomes critical priority.

---

## Render Style Boundary

**Critical:** Web UI styling is NOT video render styling.

| System | Purpose |
|--------|---------|
| Tailwind/StyleX/Astryx | Web UI only |
| Timeline DSL | Video render styles |
| FFmpeg/libass | Subtitle rendering |
| Remotion | Video player styles |

**Rule:** Never persist Tailwind classes, StyleX objects, or Astryx props into TimelineRevision.

---

## POC Implementation

**Decision:** Docs-only POC (no code changes)

**Reason:**
- Tailwind already in project
- Inline styles working well for dev console
- No need to add new dependencies yet
- Can evaluate Astryx/StyleX when /admin/* or /app/* surfaces start

---

## Status

- FRONTEND-DESIGN-SYSTEM-POC.0: COMPLETE
- Evaluation: COMPLETE
- Current baseline: INLINE_STYLES
- Tailwind: ALREADY_IN_PROJECT
- StyleX: SCOPED_POC_CANDIDATE
- Astryx: SCOPED_POC_CANDIDATE
