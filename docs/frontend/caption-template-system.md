# Caption Template System

## Overview

The caption template system manages subtitle rendering, timing, styling, and effects. It is designed to work seamlessly with Remotion for both preview and final render.

## Architecture

```
┌─────────────────────────────────────────────────┐
│              Caption Template System             │
│                                                  │
│  ┌──────────────┐  ┌──────────────┐             │
│  │   Caption    │  │   Caption    │             │
│  │   Editor     │  │   Timeline   │             │
│  └──────┬───────┘  └──────┬───────┘             │
│         │                 │                      │
│         ▼                 ▼                      │
│  ┌──────────────────────────────────┐           │
│  │        Caption Store             │           │
│  │   (Zustand + TanStack Query)     │           │
│  └──────────────┬───────────────────┘           │
│                 │                                │
│         ┌───────┴───────┐                       │
│         ▼               ▼                       │
│  ┌────────────┐  ┌────────────┐                │
│  │  Preview   │  │  RenderJob │                │
│  │  (Remotion)│  │  Builder   │                │
│  └────────────┘  └────────────┘                │
└─────────────────────────────────────────────────┘
```

## Caption Data Model

```typescript
interface Caption {
  id: string;
  text: string;
  startTime: number;      // seconds
  endTime: number;        // seconds
  style: CaptionStyle;
  templateId?: string;    // Optional template reference
  words?: WordTiming[];   // For word-level effects (karaoke)
  lines?: CaptionLine[];  // For multi-line captions
}

interface CaptionStyle {
  fontFamily: string;
  fontSize: number;
  fontColor: string;
  backgroundColor: string;
  outlineColor?: string;
  outlineWidth?: number;
  position: CaptionPosition;
  alignment: 'left' | 'center' | 'right';
  bold: boolean;
  italic: boolean;
  underline: boolean;
  opacity: number;
  animation?: CaptionAnimation;
}

interface CaptionPosition {
  x: number;  // percentage 0-100
  y: number;  // percentage 0-100
}

interface CaptionAnimation {
  type: 'fade-in' | 'slide-up' | 'slide-down' | 'pop' | 'typewriter' | 'karaoke';
  duration: number;  // seconds
  easing: 'linear' | 'ease-in' | 'ease-out' | 'ease-in-out';
}

interface WordTiming {
  word: string;
  startTime: number;
  endTime: number;
}

interface CaptionLine {
  text: string;
  words: WordTiming[];
}
```

## Template System

### Template Definition

```typescript
interface CaptionTemplate {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  thumbnailUrl: string;
  version: string;
  params: TemplateParam[];
  defaultStyle: Partial<CaptionStyle>;
  animation?: CaptionAnimation;
  compatibleProviders: string[];  // ['remotion']
}

type TemplateCategory =
  | 'subtitle'
  | 'title'
  | 'lower-third'
  | 'social'
  | 'tiktok'
  | 'youtube'
  | 'news'
  | 'karaoke'
  | 'custom';

interface TemplateParam {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'color' | 'position';
  defaultValue: unknown;
  label: string;
  description?: string;
  min?: number;
  max?: number;
  options?: { label: string; value: unknown }[];
}
```

### Built-in Templates

```typescript
const builtInTemplates: CaptionTemplate[] = [
  {
    id: 'subtitle-basic',
    name: 'Basic Subtitle',
    description: 'Simple subtitle with background',
    category: 'subtitle',
    thumbnailUrl: '/templates/subtitle-basic.png',
    version: '1.0.0',
    params: [
      { name: 'backgroundColor', type: 'color', defaultValue: '#000000', label: 'Background Color' },
      { name: 'fontColor', type: 'color', defaultValue: '#FFFFFF', label: 'Font Color' },
    ],
    defaultStyle: {
      fontSize: 24,
      fontColor: '#FFFFFF',
      backgroundColor: '#000000',
      position: { x: 50, y: 80 },
      alignment: 'center',
    },
    compatibleProviders: ['remotion'],
  },
  {
    id: 'tiktok-subtitle',
    name: 'TikTok Style',
    description: 'Bold subtitle with highlight effect',
    category: 'tiktok',
    thumbnailUrl: '/templates/tiktok-subtitle.png',
    version: '1.0.0',
    params: [
      { name: 'highlightColor', type: 'color', defaultValue: '#FFD700', label: 'Highlight Color' },
    ],
    defaultStyle: {
      fontSize: 32,
      fontColor: '#FFFFFF',
      backgroundColor: '#000000',
      position: { x: 50, y: 70 },
      alignment: 'center',
      bold: true,
    },
    animation: { type: 'pop', duration: 0.3, easing: 'ease-out' },
    compatibleProviders: ['remotion'],
  },
  {
    id: 'karaoke',
    name: 'Karaoke',
    description: 'Word-by-word highlighting',
    category: 'karaoke',
    thumbnailUrl: '/templates/karaoke.png',
    version: '1.0.0',
    params: [
      { name: 'activeColor', type: 'color', defaultValue: '#FFD700', label: 'Active Color' },
      { name: 'inactiveColor', type: 'color', defaultValue: '#888888', label: 'Inactive Color' },
    ],
    defaultStyle: {
      fontSize: 28,
      fontColor: '#888888',
      backgroundColor: 'transparent',
      position: { x: 50, y: 85 },
      alignment: 'center',
    },
    animation: { type: 'karaoke', duration: 0, easing: 'linear' },
    compatibleProviders: ['remotion'],
  },
  {
    id: 'lower-third',
    name: 'Lower Third',
    description: 'News-style lower third',
    category: 'lower-third',
    thumbnailUrl: '/templates/lower-third.png',
    version: '1.0.0',
    params: [
      { name: 'barColor', type: 'color', defaultValue: '#1E40AF', label: 'Bar Color' },
    ],
    defaultStyle: {
      fontSize: 20,
      fontColor: '#FFFFFF',
      backgroundColor: '#1E40AF',
      position: { x: 10, y: 85 },
      alignment: 'left',
    },
    animation: { type: 'slide-up', duration: 0.5, easing: 'ease-out' },
    compatibleProviders: ['remotion'],
  },
];
```

## Caption Timing

### Timing Model

```typescript
interface CaptionTiming {
  captionId: string;
  startTime: number;
  endTime: number;
  duration: number;
}

function calculateDuration(caption: Caption): number {
  return caption.endTime - caption.startTime;
}

function overlaps(a: Caption, b: Caption): boolean {
  return a.startTime < b.endTime && b.startTime < a.endTime;
}

function findOverlaps(captions: Caption[]): [Caption, Caption][] {
  const overlaps: [Caption, Caption][] = [];
  for (let i = 0; i < captions.length; i++) {
    for (let j = i + 1; j < captions.length; j++) {
      if (overlaps(captions[i], captions[j])) {
        overlaps.push([captions[i], captions[j]]);
      }
    }
  }
  return overlaps;
}
```

### Timing Adjustment

```typescript
function adjustTiming(
  caption: Caption,
  newStartTime: number,
  newEndTime: number,
  captions: Caption[]
): { caption: Caption; conflicts: Caption[] } {
  const updated = { ...caption, startTime: newStartTime, endTime: newEndTime };
  const conflicts = captions.filter(
    c => c.id !== caption.id && overlaps(updated, c)
  );
  return { caption: updated, conflicts };
}

function snapToNearestClip(
  caption: Caption,
  clips: TimelineClip[],
  threshold: number = 0.5
): Caption {
  let nearestStart = caption.startTime;
  let nearestEnd = caption.endTime;
  let minDistStart = Infinity;
  let minDistEnd = Infinity;

  for (const clip of clips) {
    const distStart = Math.abs(caption.startTime - clip.startTime);
    const distEnd = Math.abs(caption.endTime - (clip.startTime + clip.duration));
    if (distStart < minDistStart && distStart < threshold) {
      minDistStart = distStart;
      nearestStart = clip.startTime;
    }
    if (distEnd < minDistEnd && distEnd < threshold) {
      minDistEnd = distEnd;
      nearestEnd = clip.startTime + clip.duration;
    }
  }

  return { ...caption, startTime: nearestStart, endTime: nearestEnd };
}
```

## Caption Line Breaking

### Line Break Rules

```typescript
interface LineBreakOptions {
  maxCharsPerLine: number;
  maxLines: number;
  breakMode: 'word' | 'character' | 'smart';
}

const defaultLineBreakOptions: LineBreakOptions = {
  maxCharsPerLine: 40,
  maxLines: 2,
  breakMode: 'smart',
};

function breakCaptionLines(text: string, options: LineBreakOptions): string[] {
  if (text.length <= options.maxCharsPerLine) return [text];

  const lines: string[] = [];
  let remaining = text;

  while (remaining.length > 0 && lines.length < options.maxLines) {
    if (remaining.length <= options.maxCharsPerLine) {
      lines.push(remaining);
      break;
    }

    let breakPoint = options.maxCharsPerLine;
    if (options.breakMode === 'word' || options.breakMode === 'smart') {
      const lastSpace = remaining.lastIndexOf(' ', options.maxCharsPerLine);
      if (lastSpace > 0) {
        breakPoint = lastSpace;
      }
    }

    lines.push(remaining.substring(0, breakPoint).trim());
    remaining = remaining.substring(breakPoint).trim();
  }

  if (remaining.length > 0 && lines.length >= options.maxLines) {
    const lastLine = lines[lines.length - 1];
    lines[lines.length - 1] = lastLine.substring(0, lastLine.length - 3) + '...';
  }

  return lines;
}
```

## Remotion Caption Component

```tsx
import { AbsoluteFill, Sequence, Text, useCurrentFrame, interpolate } from 'remotion';
import { CaptionTemplate } from './CaptionTemplate';

interface CaptionRendererProps {
  captions: CaptionProps[];
  fontAssets: FontAsset[];
}

export function CaptionRenderer({ captions, fontAssets }: CaptionRendererProps) {
  const frame = useCurrentFrame();

  return (
    <AbsoluteFill>
      {/* Load fonts */}
      <style>
        {fontAssets.map(font => `
          @font-face {
            font-family: '${font.family}';
            src: url('${font.url}') format('${font.format}');
            font-weight: ${font.weight};
            font-style: ${font.style};
          }
        `).join('\n')}
      </style>

      {/* Render captions */}
      {captions.map(caption => (
        <Sequence
          key={caption.id}
          from={Math.floor(caption.startTime * 30)}
          durationInFrames={Math.floor((caption.endTime - caption.startTime) * 30)}
        >
          <CaptionTemplate caption={caption} />
        </Sequence>
      ))}
    </AbsoluteFill>
  );
}
```

## Caption Store (Zustand)

```typescript
import { create } from 'zustand';

interface CaptionStore {
  captions: Caption[];
  selectedCaptionId: string | null;
  currentTemplateId: string | null;

  // Actions
  setCaptions: (captions: Caption[]) => void;
  addCaption: (caption: Caption) => void;
  updateCaption: (id: string, updates: Partial<Caption>) => void;
  removeCaption: (id: string) => void;
  selectCaption: (id: string | null) => void;
  setCurrentTemplate: (templateId: string | null) => void;

  // Timing
  adjustTiming: (id: string, startTime: number, endTime: number) => void;
  snapToClips: (clipIds: string[]) => void;

  // Style
  applyStyle: (id: string, style: Partial<CaptionStyle>) => void;
  applyTemplate: (id: string, templateId: string) => void;

  // Line breaking
  autoBreakLines: (options?: LineBreakOptions) => void;
}

export const useCaptionStore = create<CaptionStore>((set, get) => ({
  captions: [],
  selectedCaptionId: null,
  currentTemplateId: 'subtitle-basic',

  setCaptions: (captions) => set({ captions }),

  addCaption: (caption) => set(state => ({
    captions: [...state.captions, caption],
  })),

  updateCaption: (id, updates) => set(state => ({
    captions: state.captions.map(c =>
      c.id === id ? { ...c, ...updates } : c
    ),
  })),

  removeCaption: (id) => set(state => ({
    captions: state.captions.filter(c => c.id !== id),
    selectedCaptionId: state.selectedCaptionId === id ? null : state.selectedCaptionId,
  })),

  selectCaption: (id) => set({ selectedCaptionId: id }),

  setCurrentTemplate: (templateId) => set({ currentTemplateId: templateId }),

  adjustTiming: (id, startTime, endTime) => set(state => ({
    captions: state.captions.map(c =>
      c.id === id ? { ...c, startTime, endTime } : c
    ),
  })),

  snapToClips: () => { /* implementation */ },

  applyStyle: (id, style) => set(state => ({
    captions: state.captions.map(c =>
      c.id === id ? { ...c, style: { ...c.style, ...style } } : c
    ),
  })),

  applyTemplate: (id, templateId) => {
    const template = builtInTemplates.find(t => t.id === templateId);
    if (!template) return;
    set(state => ({
      captions: state.captions.map(c =>
        c.id === id ? {
          ...c,
          templateId,
          style: { ...c.style, ...template.defaultStyle } as CaptionStyle,
        } : c
      ),
    }));
  },

  autoBreakLines: (options) => {
    const opts = options || defaultLineBreakOptions;
    set(state => ({
      captions: state.captions.map(c => ({
        ...c,
        lines: breakCaptionLines(c.text, opts).map((text, i) => ({
          text,
          words: c.words || [],
        })),
      })),
    }));
  },
}));
```
