# Media Platform — UI Design System Specification

> **Version:** 1.0  
> **Date:** 2026-05-24  
> **Author:** UI/UX Design Consultation  
> **Design Baseline:** 1440px desktop, dark-first  
> **Reference Products:** Linear, Vercel, Stripe, Runway, CapCut Web, Canva, Figma, Supabase, Raycast, Notion

---

## 1. Design Philosophy

### 1.1 Core Principles

| Principle | Description | Reference |
|-----------|-------------|-----------|
| **Quiet Confidence** | UI never competes with user content. The video canvas is the hero, chrome is invisible. | Linear, Figma |
| **Progressive Disclosure** | Show only what's needed. Advanced features live behind hover/click, not in permanent UI. | Notion, Raycast |
| **Spatial Memory** | Panels stay where users put them. Layout is predictable across sessions. | CapCut, DaVinci |
| **Semantic Color** | Color communicates meaning (status, tier, risk), never just decoration. | Stripe, Vercel |
| **Dark-First** | Dark mode is the primary canvas. Light mode is a derived theme, not the other way around. | Runway, Raycast |
| **Density Without Clutter** | Information density is high but breathing room is maintained through consistent spacing tokens. | Linear, Supabase |

### 1.2 Visual Personality

```
Professional creator tool + AI-powered intelligence + SaaS reliability

NOT: consumer social app, gaming UI, flashy motion graphics tool
YES: "If Linear and Figma had a baby that edited video"
```

### 1.3 What This Design Is NOT

- No neon gradients, no glassmorphism overload, no 3D floating elements
- No cartoon illustrations, no stock photo heroes
- No sidebar icons that are hard to distinguish
- No "dashboard for the sake of dashboard" — every metric earns its space

---

## 2. Color System

### 2.1 Dark Mode Palette (Primary)

```css
:root[data-theme="dark"] {
  /* Surface hierarchy — 5 levels */
  --surface-0: #0B0D10;     /* Deepest background (app shell) */
  --surface-1: #111318;     /* Main content area */
  --surface-2: #181B20;     /* Cards, panels */
  --surface-3: #1E2128;     /* Elevated cards, dropdowns */
  --surface-4: #252830;     /* Hover states, active items */

  /* Text hierarchy */
  --text-primary: #E8EAED;    /* Headings, primary content */
  --text-secondary: #9AA0AB;  /* Descriptions, labels */
  --text-tertiary: #5C6370;   /* Placeholders, disabled */
  --text-inverse: #0B0D10;    /* Text on light backgrounds */

  /* Border */
  --border-subtle: rgba(255, 255, 255, 0.06);   /* Card borders, dividers */
  --border-default: rgba(255, 255, 255, 0.10);  /* Input borders, panels */
  --border-strong: rgba(255, 255, 255, 0.16);   /* Focus rings, active states */

  /* Accent — Electric Indigo (primary action) */
  --accent-50: #EEF2FF;
  --accent-100: #E0E7FF;
  --accent-200: #C7D2FE;
  --accent-300: #A5B4FC;
  --accent-400: #818CF8;    /* Hover */
  --accent-500: #6366F1;    /* Default */
  --accent-600: #4F46E5;    /* Active/pressed */
  --accent-700: #4338CA;

  /* Semantic */
  --success: #34D399;       /* Completed, online */
  --warning: #FBBF24;       /* Queued, attention */
  --danger: #F87171;        /* Failed, destructive */
  --info: #60A5FA;          /* Information, links */

  /* Tier colors */
  --tier-free: #9AA0AB;     /* Muted gray */
  --tier-pro: #818CF8;      /* Accent indigo */
  --tier-team: #34D399;     /* Success green */
}
```

### 2.2 Light Mode Palette (Derived)

```css
:root[data-theme="light"] {
  --surface-0: #FFFFFF;
  --surface-1: #FAFBFC;
  --surface-2: #F3F4F6;
  --surface-3: #FFFFFF;
  --surface-4: #E5E7EB;

  --text-primary: #111827;
  --text-secondary: #6B7280;
  --text-tertiary: #9CA3AF;
  --text-inverse: #FFFFFF;

  --border-subtle: rgba(0, 0, 0, 0.06);
  --border-default: rgba(0, 0, 0, 0.10);
  --border-strong: rgba(0, 0, 0, 0.16);

  /* Accent stays the same */
  --accent-500: #6366F1;
}
```

### 2.3 Timeline Clip Colors

```css
--clip-video: #6366F1;       /* Indigo — primary content */
--clip-audio: #2DD4BF;       /* Teal — audio waveforms */
--clip-subtitle: #F472B6;    /* Pink — text overlay */
--clip-sticker: #FBBF24;     /* Amber — decorative */
--clip-effect: #A78BFA;      /* Violet — VFX */
--clip-ai: #22D3EE;          /* Cyan — AI-generated */
```

### 2.4 Color Usage Rules

| Element | Dark Mode | Light Mode |
|---------|-----------|------------|
| App shell background | `surface-0` (#0B0D10) | `surface-0` (#FFFFFF) |
| Main content area | `surface-1` (#111318) | `surface-1` (#FAFBFC) |
| Cards | `surface-2` (#181B20) | `surface-3` (#FFFFFF) with shadow |
| Sidebar | `surface-0` (#0B0D10) | `surface-2` (#F3F4F6) |
| Active nav item | `surface-4` (#252830) + left accent bar | `surface-0` + left accent bar |
| Primary button | `accent-500` bg + white text | `accent-600` bg + white text |
| Ghost button | transparent + `text-secondary` hover `surface-4` | transparent + `text-secondary` hover `surface-2` |
| Table header | `surface-1` + `text-tertiary` uppercase | `surface-2` + `text-tertiary` uppercase |
| Table row hover | `surface-3` | `surface-2` |
| Input focus | `border-strong` + 2px ring accent-500/20% | Same |

---

## 3. Typography System

### 3.1 Font Stack

```css
--font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
--font-mono: 'JetBrains Mono', 'Fira Code', 'SF Mono', monospace;
```

**Primary font: Inter** — Best-in-class screen readability, extensive weight coverage, open source.  
**Alternative: Geist** (Vercel's font) — If available, slightly more personality.

### 3.2 Type Scale

| Token | Size | Weight | Line Height | Use Case |
|-------|------|--------|-------------|----------|
| `text-display` | 40px / 2.5rem | 700 | 1.1 | Landing hero, empty state headline |
| `text-h1` | 30px / 1.875rem | 700 | 1.2 | Page titles |
| `text-h2` | 24px / 1.5rem | 600 | 1.3 | Section headers |
| `text-h3` | 18px / 1.125rem | 600 | 1.4 | Card titles, panel headers |
| `text-body` | 14px / 0.875rem | 400 | 1.5 | Body text, table cells, descriptions |
| `text-caption` | 12px / 0.75rem | 500 | 1.4 | Labels, badges, timestamps, metadata |
| `text-micro` | 11px / 0.6875rem | 500 | 1.3 | Table headers (uppercase), tab labels |

### 3.3 Text Color Hierarchy

```
Page title:        text-primary,   700 weight
Section header:    text-primary,   600 weight
Card title:        text-primary,   600 weight
Body:              text-secondary, 400 weight
Description:       text-secondary, 400 weight
Caption/meta:      text-tertiary,  500 weight
Placeholder:       text-tertiary,  400 weight
Disabled:          text-tertiary,  400 weight, opacity 0.5
```

---

## 4. Spacing & Layout System

### 4.1 Spacing Scale

```
--space-0:   0px
--space-1:   4px      /* Tight gaps */
--space-2:   8px      /* Component internal padding */
--space-3:   12px     /* Small gaps */
--space-4:   16px     /* Default gap */
--space-5:   20px     /* Medium gaps */
--space-6:   24px     /* Card padding */
--space-8:   32px     /* Section gaps */
--space-10:  40px     /* Large section gaps */
--space-12:  48px     /* Page-level gaps */
--space-16:  64px     /* Hero spacing */
```

### 4.2 Border Radius

```
--radius-sm:   6px     /* Badges, small buttons */
--radius-md:   8px     /* Inputs, cards */
--radius-lg:   12px    /* Modals, large cards */
--radius-xl:   16px    /* Hero cards, feature cards */
--radius-full: 9999px  /* Avatars, pills */
```

### 4.3 Shadow System

```css
/* Dark mode — subtle depth */
--shadow-sm:  0 1px 2px rgba(0, 0, 0, 0.3);
--shadow-md:  0 4px 12px rgba(0, 0, 0, 0.4);
--shadow-lg:  0 8px 24px rgba(0, 0, 0, 0.5);
--shadow-xl:  0 16px 48px rgba(0, 0, 0, 0.6);

/* Light mode — visible elevation */
--shadow-sm:  0 1px 3px rgba(0, 0, 0, 0.08);
--shadow-md:  0 4px 12px rgba(0, 0, 0, 0.10);
--shadow-lg:  0 8px 24px rgba(0, 0, 0, 0.12);
```

### 4.4 Layout Grid

```
App shell:     flex row, full viewport
Sidebar:       240px fixed (collapsed: 64px)
Header:        56px fixed height
Content:       flex-1, scroll-y
Max content:   1280px centered for dashboard/settings pages
Editor:        full-width, no max constraint
```

---

## 5. Page Architecture

### 5.1 Information Architecture

```
/                          → Landing Page (public)
/login                     → Auth
/app                       → App Shell
  /dashboard               → Dashboard (default)
  /projects                → Project list
  /projects/:id            → Video Editor
  /assets                  → Asset library
  /exports                 → Export center
  /templates               → Template gallery
  /ai                      → AI tools hub
  /billing                 → Billing & usage
  /settings                → Account settings
  /admin/*                 → Admin panel (role-gated)
```

### 5.2 Navigation Structure

**Sidebar (240px):**

```
┌─────────────────────────────┐
│  Logo          [collapse]   │
│─────────────────────────────│
│  🔍 Search (⌘K)            │
│─────────────────────────────│
│  CREATE                     │
│  [+ New Project]            │
│─────────────────────────────│
│  MAIN                       │
│  🏠 Dashboard               │
│  📁 Projects                │
│  📦 Assets                  │
│  📤 Exports                 │
│  📋 Templates               │
│─────────────────────────────│
│  AI TOOLS                   │
│  ✨ AI Studio               │
│  📝 Auto Captions           │
│  ✂️ Smart Cut               │
│─────────────────────────────│
│  WORKSPACE                  │
│  👥 Team                    │
│  💳 Billing                 │
│  ⚙️ Settings                │
│─────────────────────────────│
│  [User Avatar] [tier badge] │
└─────────────────────────────┘
```

---

## 6. Page Layouts

### 6.1 Landing Page

```
┌──────────────────────────────────────────────────────────┐
│  Header: Logo | Features | Pricing | Gallery | Login |CTA│
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  HERO SECTION                                     │   │
│  │  "AI-powered video editing, rendered your way."   │   │
│  │  Sub: Edit in browser. Export locally or cloud.    │   │
│  │                                                   │   │
│  │  [Start Editing Free]  [Watch Demo]               │   │
│  │                                                   │   │
│  │  ┌─────────────────────────────────────────┐     │   │
│  │  │                                         │     │   │
│  │  │   Editor Screenshot / Video Preview     │     │   │
│  │  │   (dark chrome, showing timeline)       │     │   │
│  │  │                                         │     │   │
│  │  └─────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  TRUST BAR                                        │   │
│  │  "Trusted by 10,000+ creators"                    │   │
│  │  [Logo] [Logo] [Logo] [Logo] [Logo]              │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  FEATURES GRID (2x3)                              │   │
│  │                                                   │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐         │   │
│  │  │ AI Clips │ │Auto      │ │ Browser  │         │   │
│  │  │ icon     │ │Captions  │ │ Export   │         │   │
│  │  │ desc     │ │ icon     │ │ icon     │         │   │
│  │  │          │ │ desc     │ │ desc     │         │   │
│  │  └──────────┘ └──────────┘ └──────────┘         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐         │   │
│  │  │ Cloud    │ │Templates │ │ Team     │         │   │
│  │  │ Render   │ │          │ │ Workspace│         │   │
│  │  └──────────┘ └──────────┘ └──────────┘         │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  SHOWCASE — Before/After or Gallery               │   │
│  │  Video thumbnails in a horizontal scroll          │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  PRICING (3 columns)                              │   │
│  │  Free | Pro (highlighted) | Team                  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  FAQ (accordion)                                  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  Footer                                                  │
└──────────────────────────────────────────────────────────┘
```

**Hero Design Details:**
- Background: `surface-0` with a subtle radial gradient from `accent-500/5%` at center
- Hero text: `text-display` (40px/700) in `text-primary`
- Subtitle: `text-body` in `text-secondary`
- Primary CTA: `accent-500` bg, white text, 48px height, `radius-md`, hover → `accent-600`
- Secondary CTA: ghost button with `border-default` outline
- Editor preview: 16:9 screenshot with `radius-lg` corners, `shadow-lg`, subtle `border-subtle`
- The preview should show the actual editor UI (dark chrome) — not a mockup

**Feature Cards:**
- `surface-2` background, `radius-lg`, `border-subtle` border
- Icon: 40px, `accent-500/10%` bg circle, `accent-400` icon
- Title: `text-h3` in `text-primary`
- Description: `text-body` in `text-secondary`, 2-3 lines max
- Hover: `border-strong`, `shadow-md`, slight translateY(-2px)

**Pricing Cards:**
- Free/Team: `surface-2` bg, `border-subtle`
- Pro: `surface-2` bg, `border-accent-500/30%`, subtle `accent-500/5%` top gradient
- "Most Popular" badge: `accent-500` bg pill on Pro card
- Price: `text-h1` in `text-primary` with `/month` in `text-tertiary`
- Feature list: checkmark icons in `success`, cross icons in `text-tertiary`
- CTA button: Pro gets `accent-500` filled, others get ghost outline

---

### 6.2 Dashboard

```
┌──────────┬────────────────────────────────────────────────┐
│ Sidebar  │  Header: [Search ⌘K] [+ New] [🔔] [Avatar]   │
│ (240px)  ├────────────────────────────────────────────────┤
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ QUICK ACTIONS                            │   │
│          │  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐            │   │
│          │  │ │New │ │Upload│ │AI  │ │From│            │   │
│          │  │ │Proj│ │Asset│ │Gen │ │Tmpl│            │   │
│          │  │ └────┘ └────┘ └────┘ └────┘            │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ USAGE THIS MONTH                         │   │
│          │  │                                          │   │
│          │  │ ┌────────┐ ┌────────┐ ┌────────┐       │   │
│          │  │ │Exports │ │AI Tasks│ │Storage │       │   │
│          │  │ │ 12/50  │ │  3/20  │ │ 2.1 GB │       │   │
│          │  │ │ ████░░ │ │ ██░░░░ │ │ █░░░░░ │       │   │
│          │  │ └────────┘ └────────┘ └────────┘       │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ ACTIVE EXPORTS                           │   │
│          │  │ ┌──────────────────────────────────┐    │   │
│          │  │ │ 🎬 Summer Ad 1080p  ████░ 67%    │    │   │
│          │  │ │ 🎬 Tutorial 720p    ✅ Done      │    │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ RECENT PROJECTS                          │   │
│          │  │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   │   │
│          │  │ │thumb │ │thumb │ │thumb │ │thumb │   │   │
│          │  │ │name  │ │name  │ │name  │ │name  │   │   │
│          │  │ │date  │ │date  │ │date  │ │date  │   │   │
│          │  │ └──────┘ └──────┘ └──────┘ └──────┘   │   │
│          │  └─────────────────────────────────────────┘   │
└──────────┴────────────────────────────────────────────────┘
```

**Dashboard Design Details:**

**Quick Actions Grid:**
- 4 cards in a row, `surface-2` bg, `radius-md`, `border-subtle`
- Icon: 32px, centered, `accent-500/10%` bg
- Label: `text-caption` centered below icon
- Hover: `surface-4` bg, `border-default`

**Usage Meters:**
- 3 metric cards in a row
- Each card: `surface-2` bg, `radius-md`, `p-5`
- Label: `text-caption` in `text-tertiary`, uppercase
- Value: `text-h2` in `text-primary` with `/limit` in `text-tertiary`
- Progress bar: 4px height, `surface-4` track, `accent-500` fill (or `warning` when >80%, `danger` when >95%)
- Percentage text: `text-caption` right-aligned

**Active Exports:**
- List style, each row: `surface-2` bg on hover
- Project name: `text-body` in `text-primary`
- Status: StatusBadge component (see below)
- Progress bar: inline, 120px wide

**Recent Projects Grid:**
- 4 columns, responsive
- Card: `surface-2` bg, `radius-lg`, `border-subtle`
- Thumbnail: 16:9 aspect ratio, `radius-md` top corners, `surface-4` placeholder
- Name: `text-body` in `text-primary`, truncated
- Meta: `text-caption` in `text-tertiary` (date, duration)
- Hover: `border-default`, `shadow-md`

---

### 6.3 Video Editor

```
┌──────────────────────────────────────────────────────────────────┐
│ Toolbar: [Project Name] [↩ ↪] [💾 Saved] [Preview] [Export ▼]  │
├────────┬──────────────────────────────────┬──────────────────────┤
│ Left   │                                  │  Right Panel         │
│ Panel  │  ┌────────────────────────────┐  │  (contextual)        │
│ (280px)│  │                            │  │  (280px)             │
│        │  │     VIDEO PREVIEW          │  │                      │
│ ┌────┐ │  │     16:9 Canvas            │  │  Properties:         │
│ │Tabs│ │  │                            │  │  Position: X Y       │
│ │Mine│ │  │     ┌──────────────┐       │  │  Scale: 100%         │
│ │Tmpl│ │  │     │              │       │  │  Opacity: 100%       │
│ │Sub │ │  │     │   Video      │       │  │  Rotation: 0°        │
│ │Music│ │  │     │   Frame      │       │  │                      │
│ │AI  │ │  │     │              │       │  │  Transform:          │
│ └────┘ │  │     └──────────────┘       │  │  [Position] [Scale]  │
│        │  │                            │  │                      │
│ Content│  └────────────────────────────┘  │  Audio:              │
│ area   │                                  │  Volume: ████░ 80%   │
│        │                                  │  Fade In: 0.5s       │
│ ┌────┐ │                                  │  Fade Out: 0.5s      │
│ │    │ │                                  │                      │
│ │clip│ │                                  │  (or Subtitle:)      │
│ │thumb│ │                                 │  Text: [input]       │
│ │    │ │                                  │  Font: Inter         │
│ └────┘ │                                  │  Size: 24px          │
│        │                                  │  Color: #FFFFFF      │
├────────┴──────────────────────────────────┴──────────────────────┤
│ TIMELINE                                                         │
│ ┌─────┬──────────────────────────────────────────────────────┐   │
│ │00:00│05   10   15   20   25   30   35   40   45   50   55│   │
│ ├─────┼──────────────────────────────────────────────────────┤   │
│ │Video│████████████████████░░░░████████████████░░░░██████████│   │
│ │Audio│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░▓▓▓▓▓▓▓▓│   │
│ │Sub  │    ┌──────────┐         ┌──────────┐                │   │
│ │     │    │Hello world│         │Thank you │                │   │
│ │Music│░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │
│ └─────┴──────────────────────────────────────────────────────┘   │
│ [zoom −] [zoom +] [fit] [snap] [◀] [▶] [⏺] 00:15 / 01:30     │
└──────────────────────────────────────────────────────────────────┘
```

**Editor Design Details:**

**Toolbar (48px height):**
- Background: `surface-0` with `border-subtle` bottom border
- Project name: editable inline, `text-body` in `text-primary`
- Undo/Redo: ghost icon buttons, `text-tertiary` when disabled
- Save status: `text-caption` in `text-tertiary` ("Saved" / "Saving..." / "Unsaved")
- Preview button: ghost button with play icon
- Export button: `accent-500` bg, white text, `radius-sm`, 32px height — the most prominent element in the toolbar

**Left Panel (280px):**
- Background: `surface-1`
- Tab bar at top: 5 tabs (Media, Templates, Subtitles, Music, AI)
- Active tab: `text-primary` + bottom `accent-500` border (2px)
- Inactive tab: `text-tertiary`
- Content area: scrollable, `p-3`
- Asset thumbnails: grid of 2 columns, `radius-sm`, 16:9 aspect
- Hover: `border-default`, slight scale(1.02)

**Video Preview (center, flex-1):**
- Background: `#000000` (pure black for accurate color preview)
- Canvas: centered, letterboxed to 16:9
- Maximum width: constrained by available space
- Playback controls overlay: bottom center, `surface-0/80%` bg pill, appears on hover

**Right Panel (280px, contextual):**
- Shows when an element is selected
- Background: `surface-1`
- Sections: collapsible, `text-caption` uppercase headers
- Inputs: `surface-2` bg, `border-subtle`, `radius-sm`, 32px height
- Sliders: custom styled, track `surface-4`, thumb `accent-500`
- Color picker: small square preview + hex input

**Timeline (bottom, variable height ~200px):**
- Background: `surface-0`
- Track headers (left, fixed 80px): `surface-1`, track name in `text-caption`
- Ruler: `text-micro` in `text-tertiary`, tick marks every 5s
- Playhead: `accent-500` vertical line with triangle handle at top
- Video clips: `clip-video` bg with `radius-sm`, thumbnail strip inside
- Audio clips: `clip-audio` bg with waveform visualization
- Subtitle clips: `clip-subtitle` bg with text preview
- Clip handles: left/right edges for trimming, cursor changes on hover
- Transport controls: bottom-left of timeline, `text-secondary` icons
- Zoom: bottom-right, slider + fit button

**Export Modal (when clicking Export):**
```
┌─────────────────────────────────────────────┐
│  Export Video                          [✕]  │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────┐ ┌─────────────────┐   │
│  │ 🌐 Browser      │ │ ☁️ Cloud         │   │
│  │    Export        │ │    Render       │   │
│  │                  │ │                  │   │
│  │ 720p             │ │ 1080p / 4K      │   │
│  │ With watermark   │ │ No watermark    │   │
│  │ Free             │ │ Pro plan        │   │
│  │ ⚠️ Page must     │ │ ✅ Close page    │   │
│  │ stay open        │ │ freely          │   │
│  │                  │ │                  │   │
│  │ [Select]         │ │ [Select]  ⭐    │   │
│  └─────────────────┘ └─────────────────┘   │
│                                             │
│  Format: [MP4 ▼]   Resolution: [1080p ▼]   │
│  FPS: [30 ▼]       Codec: [H.264 ▼]        │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │ Estimated: ~2 min | Size: ~45 MB    │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  [Cancel]                    [Export Video] │
└─────────────────────────────────────────────┘
```

---

### 6.4 Export Center

```
┌──────────┬────────────────────────────────────────────────┐
│ Sidebar  │  Header: [Export Center]  [Filter ▼] [Refresh] │
│          ├────────────────────────────────────────────────┤
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ TABS: [All] [Rendering] [Completed]     │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ TABLE                                     │   │
│          │  │                                          │   │
│          │  │ Project    Res   Mode   Status  Time  Act│   │
│          │  │ ──────────────────────────────────────── │   │
│          │  │ Summer Ad  1080p Cloud  ████ 67%  2m  ⏸ │   │
│          │  │ Tutorial   720p  Local  ✅ Done  5m  ⬇️ │   │
│          │  │ Promo      4K    Cloud  ⚠️ Fail  --  🔄│   │
│          │  │ Reel #12   1080p Cloud  ⏳ Queue  --  ⏸ │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ INFO BOX                                  │   │
│          │  │ 🌐 Browser exports: Free, 720p, watermark│   │
│          │  │ ☁️ Cloud exports: Pro, up to 4K, no mark  │   │
│          │  └─────────────────────────────────────────┘   │
└──────────┴────────────────────────────────────────────────┘
```

**Export Table Details:**
- Table: `DataTableShell` component
- Status column: `StatusBadge` with colored dot
  - Queued: `warning` dot + "Queued"
  - Rendering: `accent` dot + progress bar (inline, 120px)
  - Completed: `success` dot + "Done"
  - Failed: `danger` dot + "Failed" + hover reveals reason
  - Expired: `text-tertiary` dot + "Expired"
- Actions column: icon buttons (download, retry, view logs, cancel)
- Failed rows: subtle `danger/5%` background tint

---

### 6.5 Billing Page

```
┌──────────┬────────────────────────────────────────────────┐
│ Sidebar  │  Header: [Billing & Usage]                     │
│          ├────────────────────────────────────────────────┤
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ CURRENT PLAN                              │   │
│          │  │ ┌─────────────────────────────────────┐  │   │
│          │  │ │ Pro Plan — $19/month                 │  │   │
│          │  │ │ Next billing: June 15, 2026          │  │   │
│          │  │ │ [Manage Subscription] [Cancel]       │  │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ USAGE THIS MONTH                          │   │
│          │  │                                          │   │
│          │  │ Cloud Renders    12 / 50     ██████░░░░  │   │
│          │  │ AI Tasks          3 / 20     ████░░░░░░  │   │
│          │  │ Storage         2.1 / 10 GB  ██░░░░░░░░  │   │
│          │  │ Projects          5 / 20     ██░░░░░░░░  │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ COMPARE PLANS                             │   │
│          │  │                                          │   │
│          │  │ ┌──────────┐ ┌──────────┐ ┌──────────┐  │   │
│          │  │ │ Free     │ │ Pro  ⭐  │ │ Team     │  │   │
│          │  │ │ $0       │ │ $19/mo   │ │ $49/mo   │  │   │
│          │  │ │ 720p     │ │ 1080p/4K │ │ 1080p/4K │  │   │
│          │  │ │ Watermark│ │ No mark  │ │ No mark  │  │   │
│          │  │ │ 5 proj   │ │ 20 proj  │ │ Unlimited│  │   │
│          │  │ │ [Current]│ │ [Upgrade]│ │ [Upgrade]│  │   │
│          │  │ └──────────┘ └──────────┘ └──────────┘  │   │
│          │  └─────────────────────────────────────────┘   │
│          │                                                │
│          │  ┌─────────────────────────────────────────┐   │
│          │  │ PAYMENT HISTORY                           │   │
│          │  │ Invoice    Date       Amount   Status    │   │
│          │  │ #00123     May 15     $19.00   ✅ Paid   │   │
│          │  │ #00112     Apr 15     $19.00   ✅ Paid   │   │
│          │  └─────────────────────────────────────────┘   │
└──────────┴────────────────────────────────────────────────┘
```

---

## 7. Component Design

### 7.1 Sidebar Navigation

**States:**
- Default: `text-secondary`, `surface-0` bg
- Hover: `surface-4` bg
- Active: `surface-4` bg, `accent-500` left border (3px), `text-primary`
- Collapsed: 64px width, icons only, tooltip on hover

**Section Headers:**
- `text-micro` in `text-tertiary`, uppercase, 16px left padding
- 12px bottom margin

### 7.2 Project Card

```
┌──────────────────────────┐
│ ┌──────────────────────┐ │  ← 16:9 thumbnail
│ │                      │ │     surface-4 placeholder
│ │   video thumbnail    │ │     radius-md top corners
│ │                      │ │
│ └──────────────────────┘ │
│                          │
│  Project Name            │  ← text-body, text-primary, truncated
│  Edited 2h ago · 1:30   │  ← text-caption, text-tertiary
│  ┌──────┐               │
│  │ Free │               │  ← tier badge (optional)
│  └──────┘               │
└──────────────────────────┘
```

- Card bg: `surface-2`, `radius-lg`, `border-subtle`
- Hover: `border-default`, `shadow-md`, translateY(-1px)
- Context menu: right-click or `···` button

### 7.3 Status Badge

```
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│ ● Active│  │ ● Queue │  │ ● Render│  │ ● Done  │  │ ● Failed│
└─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
  accent       warning       accent       success       danger
```

- 6px dot + text-caption
- Pill shape: `radius-full`, `px-2 py-0.5`
- Background: semantic color at 10% opacity

### 7.4 Timeline Components

**Track Header:**
- 80px wide, `surface-1` bg
- Icon (16px) + track name (`text-caption`)
- Mute/Solo/Lock toggle buttons (16px icons)

**Clip Block:**
- Height: 48px (video), 36px (audio), 32px (subtitle)
- Video: `clip-video` bg at 20% opacity, `clip-video` left border (3px)
- Audio: `clip-audio` bg at 20% opacity, waveform SVG inside
- Subtitle: `clip-subtitle` bg at 20% opacity, text preview truncated
- Selected: brighter bg, `border-strong` outline
- Resize handles: 4px wide, cursor: col-resize

**Playhead:**
- 2px line in `accent-500`
- Triangle handle: 12px wide, `accent-500` fill
- Snaps to clip edges when dragging

### 7.5 AI Tool Card

```
┌──────────────────────────┐
│  ✨ Auto Captions         │  ← icon + text-h3
│                          │
│  Generate subtitles      │  ← text-body, text-secondary
│  automatically from      │
│  audio using AI          │
│                          │
│  [Run]                   │  ← accent-500 button
│                          │
│  ⏱ ~30s · Pro feature   │  ← text-caption, text-tertiary
└──────────────────────────┘
```

- Card bg: `surface-2`, `radius-lg`, `border-subtle`
- Icon: 32px, `accent-500/10%` circle bg
- Hover: `border-default`, `shadow-sm`

### 7.6 Pricing Card

- Free: `surface-2` bg, `border-subtle`
- Pro (recommended): `surface-2` bg, `border-accent-500/30%`, top accent gradient stripe (3px, `accent-500` to `accent-400`)
- Team: `surface-2` bg, `border-subtle`
- "Most Popular" badge: `accent-500` bg pill, 11px text, positioned above card

### 7.7 Usage Meter

```
Label:         text-caption, text-tertiary, uppercase
Value:         text-h3, text-primary
  /limit:      text-body, text-tertiary
Bar:           4px height, surface-4 track
  fill:        accent-500 (normal), warning-500 (>80%), danger-500 (>95%)
Percentage:    text-caption, right-aligned, text-tertiary
```

### 7.8 Export Modal

- Overlay: `surface-0/60%` with backdrop-blur(8px)
- Modal: `surface-2` bg, `radius-xl`, `shadow-xl`, max-width 560px
- Two option cards side by side (Browser vs Cloud)
- Selected option: `accent-500` border, `accent-500/5%` bg
- Unselected: `border-subtle`, hover `border-default`
- Footer: Cancel (ghost) + Export (accent-500 primary)

### 7.9 Toast Notification

```
┌────────────────────────────────────────┐
│ ✅ Export completed — summer-ad.mp4   │
│                            [Download] [✕]│
└────────────────────────────────────────┘
```

- Position: bottom-right, stacked
- Bg: `surface-3`, `radius-md`, `shadow-lg`, `border-subtle`
- Success: `success` left border (3px)
- Error: `danger` left border
- Auto-dismiss: 5s with progress bar at bottom
- Max width: 400px

### 7.10 Empty State

```
         ┌─────────┐
         │  icon   │  ← 48px, text-tertiary
         └─────────┘

     No projects yet      ← text-h3, text-primary

  Create your first project ← text-body, text-secondary
  to get started.

     [+ New Project]      ← accent-500 primary button
```

- Centered, `py-16`
- Icon: Lucide icon, 48px, `text-tertiary`
- CTA: optional, `accent-500` button

### 7.11 Loading Skeleton

```css
.skeleton {
  background: linear-gradient(
    90deg,
    surface-3 25%,
    surface-4 50%,
    surface-3 75%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s ease-in-out infinite;
  border-radius: radius-md;
}
```

- Used for: project cards, table rows, metric values
- Maintains exact layout dimensions of loaded content
- Shimmer animation: left-to-right, 1.5s cycle

---

## 8. Interaction States

### 8.1 Button States

| State | Primary Button | Ghost Button | Danger Button |
|-------|---------------|--------------|---------------|
| Default | `accent-500` bg, white text | transparent, `text-secondary` | `danger` bg, white text |
| Hover | `accent-600` bg | `surface-4` bg, `text-primary` | `danger` bg, brightness(1.1) |
| Active/Pressed | `accent-700` bg | `surface-4` bg | `danger` bg, brightness(0.9) |
| Focus | + 2px ring `accent-500/30%` | + 2px ring `accent-500/30%` | + 2px ring `danger/30%` |
| Disabled | `surface-4` bg, `text-tertiary`, cursor:not-allowed | `text-tertiary`, opacity 0.5 | Same as primary disabled |
| Loading | spinner replaces text, pointer-events:none | — | — |

### 8.2 Input States

| State | Visual |
|-------|--------|
| Default | `surface-2` bg, `border-subtle` |
| Hover | `border-default` |
| Focus | `border-accent-500`, 2px ring `accent-500/20%` |
| Error | `border-danger`, error text below in `danger` |
| Disabled | `surface-1` bg, `text-tertiary`, cursor:not-allowed |

### 8.3 Hover Effects

| Element | Hover Effect |
|---------|-------------|
| Card | `border-default`, `shadow-md`, translateY(-1px), transition 150ms |
| Table row | `surface-3` bg (dark) / `surface-2` bg (light) |
| Sidebar item | `surface-4` bg |
| Clip on timeline | brighter bg, border visible |
| Button | color shift, no scale |
| Link | underline appears |

### 8.4 Focus Management

- All interactive elements: visible focus ring (2px, `accent-500/30%`)
- Tab order follows visual layout
- Modal: focus trap, Escape to close
- Dropdown: arrow keys navigate, Enter selects

### 8.5 Transitions

```css
--ease-default: cubic-bezier(0.4, 0, 0.2, 1);
--ease-in: cubic-bezier(0.4, 0, 1, 1);
--ease-out: cubic-bezier(0, 0, 0.2, 1);

--duration-fast: 100ms;    /* Button press, toggle */
--duration-normal: 150ms;  /* Hover, focus, color change */
--duration-slow: 250ms;    /* Panel slide, modal open */
--duration-slower: 350ms;  /* Page transition */
```

---

## 9. Error & Empty States

### 9.1 Error State Pattern

```
         ┌─────────┐
         │  ⚠️     │  ← 48px, danger color
         └─────────┘

     Something went wrong   ← text-h3, text-primary

  We couldn't load your     ← text-body, text-secondary
  projects. Please try
  again.

     [Try Again]           ← ghost button
     [Contact Support]     ← text-link
```

### 9.2 Specific Error States

| Context | Title | Description | Action |
|---------|-------|-------------|--------|
| Export failed | Export failed | "The render encountered an error. Your project is safe." | [Retry] [View Logs] |
| Network error | Connection lost | "Check your internet connection." | [Retry] |
| Quota exceeded | Quota reached | "You've used all 50 cloud renders this month." | [Upgrade Plan] |
| 403 Forbidden | Access denied | "You don't have permission to view this." | [Back to Dashboard] |
| 404 Not found | Page not found | "The page you're looking for doesn't exist." | [Go Home] |

### 9.3 Empty States

| Context | Icon | Title | Description | CTA |
|---------|------|-------|-------------|-----|
| No projects | 📁 | No projects yet | Create your first project to get started. | [+ New Project] |
| No exports | 📤 | No exports | Your export history will appear here. | [Go to Editor] |
| No assets | 📦 | No assets uploaded | Upload videos, images, and audio files. | [Upload] |
| No AI results | ✨ | No AI suggestions | Run an AI tool to see results here. | [Open AI Studio] |
| No search results | 🔍 | No results | Try a different search term. | — |

---

## 10. Tailwind CSS Implementation Guide

### 10.1 Updated `tailwind.config.js`

```javascript
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        surface: {
          0: 'var(--surface-0)',
          1: 'var(--surface-1)',
          2: 'var(--surface-2)',
          3: 'var(--surface-3)',
          4: 'var(--surface-4)',
        },
        text: {
          primary: 'var(--text-primary)',
          secondary: 'var(--text-secondary)',
          tertiary: 'var(--text-tertiary)',
          inverse: 'var(--text-inverse)',
        },
        border: {
          subtle: 'var(--border-subtle)',
          DEFAULT: 'var(--border-default)',
          strong: 'var(--border-strong)',
        },
        accent: {
          50: 'var(--accent-50)',
          100: 'var(--accent-100)',
          200: 'var(--accent-200)',
          300: 'var(--accent-300)',
          400: 'var(--accent-400)',
          500: 'var(--accent-500)',
          600: 'var(--accent-600)',
          700: 'var(--accent-700)',
        },
        clip: {
          video: 'var(--clip-video)',
          audio: 'var(--clip-audio)',
          subtitle: 'var(--clip-subtitle)',
          sticker: 'var(--clip-sticker)',
          effect: 'var(--clip-effect)',
          ai: 'var(--clip-ai)',
        },
        tier: {
          free: 'var(--tier-free)',
          pro: 'var(--tier-pro)',
          team: 'var(--tier-team)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      borderRadius: {
        sm: '6px',
        md: '8px',
        lg: '12px',
        xl: '16px',
      },
      boxShadow: {
        'card': '0 1px 3px rgba(0,0,0,0.3)',
        'card-hover': '0 4px 12px rgba(0,0,0,0.4)',
        'modal': '0 16px 48px rgba(0,0,0,0.6)',
      },
      animation: {
        'skeleton': 'skeleton 1.5s ease-in-out infinite',
        'fade-in': 'fadeIn 150ms ease-out',
        'slide-up': 'slideUp 250ms ease-out',
      },
      keyframes: {
        skeleton: {
          '0%': { backgroundPosition: '200% 0' },
          '100%': { backgroundPosition: '-200% 0' },
        },
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
}
```

### 10.2 Updated CSS Variables (`tokens.css`)

```css
:root[data-theme="dark"] {
  --surface-0: #0B0D10;
  --surface-1: #111318;
  --surface-2: #181B20;
  --surface-3: #1E2128;
  --surface-4: #252830;

  --text-primary: #E8EAED;
  --text-secondary: #9AA0AB;
  --text-tertiary: #5C6370;
  --text-inverse: #0B0D10;

  --border-subtle: rgba(255,255,255,0.06);
  --border-default: rgba(255,255,255,0.10);
  --border-strong: rgba(255,255,255,0.16);

  --accent-50: #EEF2FF;
  --accent-100: #E0E7FF;
  --accent-200: #C7D2FE;
  --accent-300: #A5B4FC;
  --accent-400: #818CF8;
  --accent-500: #6366F1;
  --accent-600: #4F46E5;
  --accent-700: #4338CA;

  --success: #34D399;
  --warning: #FBBF24;
  --danger: #F87171;
  --info: #60A5FA;

  --clip-video: #6366F1;
  --clip-audio: #2DD4BF;
  --clip-subtitle: #F472B6;
  --clip-sticker: #FBBF24;
  --clip-effect: #A78BFA;
  --clip-ai: #22D3EE;
}

:root[data-theme="light"] {
  --surface-0: #FFFFFF;
  --surface-1: #FAFBFC;
  --surface-2: #F3F4F6;
  --surface-3: #FFFFFF;
  --surface-4: #E5E7EB;

  --text-primary: #111827;
  --text-secondary: #6B7280;
  --text-tertiary: #9CA3AF;
  --text-inverse: #FFFFFF;

  --border-subtle: rgba(0,0,0,0.06);
  --border-default: rgba(0,0,0,0.10);
  --border-strong: rgba(0,0,0,0.16);

  /* Accent stays identical */
  --accent-500: #6366F1;
}
```

### 10.3 Component Class Patterns

```html
<!-- Card -->
<div class="bg-surface-2 border border-border-subtle rounded-lg p-6
            hover:border-border-default hover:shadow-card-hover hover:-translate-y-px
            transition-all duration-150">

<!-- Primary Button -->
<button class="bg-accent-500 text-white rounded-md px-4 py-2 text-sm font-medium
               hover:bg-accent-600 active:bg-accent-700
               focus:outline-none focus:ring-2 focus:ring-accent-500/30
               disabled:bg-surface-4 disabled:text-text-tertiary disabled:cursor-not-allowed
               transition-colors duration-100">

<!-- Ghost Button -->
<button class="text-text-secondary rounded-md px-4 py-2 text-sm font-medium
               hover:bg-surface-4 hover:text-text-primary
               focus:outline-none focus:ring-2 focus:ring-accent-500/30
               transition-colors duration-100">

<!-- Input -->
<input class="bg-surface-2 border border-border-subtle rounded-md px-3 py-2 text-sm
              text-text-primary placeholder:text-text-tertiary
              focus:border-accent-500 focus:ring-2 focus:ring-accent-500/20 focus:outline-none
              transition-colors duration-100">

<!-- Status Badge -->
<span class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium
             bg-success/10 text-success">
  <span class="w-1.5 h-1.5 rounded-full bg-success"></span>
  Completed
</span>

<!-- Table Header -->
<th class="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider
           text-text-tertiary bg-surface-1 border-b border-border-subtle">

<!-- Table Row -->
<tr class="hover:bg-surface-3 transition-colors duration-100">
  <td class="px-4 py-3 text-sm text-text-primary border-b border-border-subtle">

<!-- Sidebar Item -->
<a class="flex items-center gap-3 px-4 py-2 rounded-md text-sm
          text-text-secondary hover:bg-surface-4 hover:text-text-primary
          aria-[current=page]:bg-surface-4 aria-[current=page]:text-text-primary
          aria-[current=page]:border-l-3 aria-[current=page]:border-accent-500
          transition-colors duration-100">

<!-- Skeleton -->
<div class="bg-gradient-to-r from-surface-3 via-surface-4 to-surface-3
            bg-[length:200%_100%] animate-skeleton rounded-md">
```

---

## 11. Accessibility

### 11.1 Color Contrast

All text/background combinations must meet WCAG 2.1 AA:
- `text-primary` on `surface-2`: contrast ratio ≥ 7:1 (AAA)
- `text-secondary` on `surface-2`: contrast ratio ≥ 4.5:1 (AA)
- `text-tertiary` on `surface-2`: contrast ratio ≥ 3:1 (AA large text)
- `accent-500` on white: contrast ratio ≥ 4.5:1

### 11.2 Keyboard Navigation

| Key | Action |
|-----|--------|
| Tab / Shift+Tab | Move focus between interactive elements |
| Enter / Space | Activate button, select item |
| Escape | Close modal, dropdown, deselect |
| Arrow keys | Navigate within list, timeline, tabs |
| ⌘K | Open search |
| ⌘Z / ⌘⇧Z | Undo / Redo |
| ⌘S | Save |
| ⌘E | Export |

### 11.3 Screen Reader

- All images: `alt` text
- Icons: `aria-label` or `sr-only` text
- Dynamic content: `aria-live="polite"` for toasts, progress updates
- Modals: `role="dialog"`, `aria-modal="true"`, `aria-labelledby`
- Tabs: `role="tablist"` / `role="tab"` / `role="tabpanel"`
- Timeline: `role="slider"` for playhead, scrubber

---

## 12. Responsive Breakpoints

| Breakpoint | Width | Behavior |
|------------|-------|----------|
| Mobile | < 768px | Sidebar collapses to icon-only, timeline stacks vertically, editor not recommended |
| Tablet | 768–1024px | Sidebar 64px collapsed, panels overlay |
| Desktop | 1024–1440px | Full layout, panels 240px |
| Wide | > 1440px | Full layout, panels 280px, more timeline space |

**Editor is optimized for 1440px+ desktop.** Mobile editing is out of scope for v1.

---

*This specification is the design source of truth. All frontend implementation should reference these tokens, patterns, and layouts. When in doubt, look at Linear's density, Vercel's restraint, and Stripe's polish.*
