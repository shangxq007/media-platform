# Remotion Integration

## Overview

This document describes how Remotion is integrated into the React frontend for video preview and composition.

## Why Remotion

Remotion is a React-based video composition framework. It allows:

1. **React-based video composition**: Videos are defined as React components
2. **Server-side rendering**: Videos can be rendered on the server via `npx remotion render`
3. **Consistent preview and render**: The same React component is used for both preview and final render
4. **Font management**: Custom fonts can be loaded and used in compositions
5. **Template system**: Templates are React components with configurable props

## Remotion Player

### Usage

```tsx
import { Player } from '@remotion/player';
import { MainComposition } from '@/remotion/compositions/MainComposition';

function RemotionPlayerWrapper({ previewProps }: { previewProps: PreviewProps }) {
  return (
    <Player
      component={MainComposition}
      compositionWidth={previewProps.canvas.width}
      compositionHeight={previewProps.canvas.height}
      fps={previewProps.canvas.fps}
      durationInFrames={calculateDuration(previewProps)}
      inputProps={{
        clips: previewProps.clips,
        captions: previewProps.captions,
        template: previewProps.template,
      }}
      style={{ width: '100%', height: '100%' }}
    />
  );
}
```

### Player Configuration

```tsx
interface PlayerConfig {
  compositionWidth: number;
  compositionHeight: number;
  fps: number;
  durationInFrames: number;
  inputProps: CompositionInputProps;
}

const defaultConfig: PlayerConfig = {
  compositionWidth: 1920,
  compositionHeight: 1080,
  fps: 30,
  durationInFrames: 900,  // 30 seconds
};
```

## Remotion Composition

### Composition Definition

```tsx
import { Composition } from 'remotion';
import { MainComposition } from './MainComposition';

export function MainCompositionDef() {
  return (
    <Composition
      id="main-editor-composition"
      component={MainComposition}
      durationInFrames={900}
      fps={30}
      width={1920}
      height={1080}
      defaultProps={{
        clips: [],
        captions: { items: [], fontAssets: [] },
        template: null,
      }}
    />
  );
}
```

### Composition Component

```tsx
import { AbsoluteFill, Sequence, Video, Img } from 'remotion';
import { CaptionOverlay } from './CaptionOverlay';
import { TemplateOverlay } from './TemplateOverlay';

export interface MainCompositionProps {
  clips: ClipProps[];
  captions: CaptionsProps;
  template: TemplateProps | null;
}

export function MainComposition({ clips, captions, template }: MainCompositionProps) {
  return (
    <AbsoluteFill>
      {/* Render clips */}
      {clips.map((clip, index) => (
        <Sequence key={clip.id} from={clip.startFrame} durationInFrames={clip.durationFrames}>
          <Video src={clip.src} />
        </Sequence>
      ))}

      {/* Render captions */}
      <CaptionOverlay captions={captions} />

      {/* Render template overlay */}
      {template && <TemplateOverlay template={template} />}
    </AbsoluteFill>
  );
}
```

## Sharing Composition Between Frontend and Backend

### The Same Composition

The Remotion Composition used in the frontend Player is the same Composition used by the backend RemotionRenderProvider:

```
Frontend                          Backend
────────                          ───────
RemotionPlayer                    npx remotion render
    │                                 │
    ▼                                 ▼
MainComposition.tsx ──────────► MainComposition.tsx
    │                                 │
    ▼                                 ▼
CaptionOverlay.tsx ───────────► CaptionOverlay.tsx
    │                                 │
    ▼                                 ▼
FontLoader.tsx ───────────────► FontLoader.tsx
```

### How It Works

1. **Composition is authored once** as a React component
2. **Frontend** uses it with `<Player>` for preview
3. **Backend** uses it with `npx remotion render` for final output
4. **Same inputProps** ensures consistent output
5. **Same font assets** ensures consistent typography

### Shared Package

Consider extracting shared components into a shared package:

```
packages/
  remotion-common/
    src/
      compositions/
        MainComposition.tsx
        CaptionComposition.tsx
      captions/
        CaptionOverlay.tsx
        CaptionRenderer.tsx
      effects/
        EffectRenderer.tsx
      fonts/
        FontLoader.tsx
        FontManifest.tsx
      templates/
        TemplateRenderer.tsx
      types/
        index.ts
```

## Remotion Provider Backend Integration

### Backend RenderJob

```json
{
  "compositionId": "main-editor-composition",
  "templateId": "caption-template-001",
  "propsJson": "{ \"clips\": [...], \"captions\": {...}, \"template\": {...} }",
  "fontFamily": "NotoSansCJK",
  "fontAssetUri": "s3://fonts/NotoSansCJK.ttf",
  "outputUri": "s3://output/video.mp4"
}
```

### Backend Render Command

```bash
npx remotion render main-editor-composition output.mp4 \
  --props='{"clips":[...],"captions":{...},"template":{...}}'
```

## Caption Template System

### Caption Template Component

```tsx
import { AbsoluteFill, Text } from 'remotion';

interface CaptionTemplateProps {
  text: string;
  x: number;
  y: number;
  fontFamily: string;
  fontSize: number;
  fontColor: string;
  backgroundColor: string;
  effect?: 'fade-in' | 'slide-up' | 'highlight' | 'karaoke';
}

export function CaptionTemplate({
  text,
  x,
  y,
  fontFamily,
  fontSize,
  fontColor,
  backgroundColor,
  effect = 'fade-in',
}: CaptionTemplateProps) {
  return (
    <AbsoluteFill
      style={{
        left: `${x}%`,
        top: `${y}%`,
        transform: 'translate(-50%, -50%)',
      }}
    >
      <div
        style={{
          backgroundColor,
          padding: '8px 16px',
          borderRadius: '4px',
          fontFamily,
          fontSize: `${fontSize}px`,
          color: fontColor,
        }}
      >
        {text}
      </div>
    </AbsoluteFill>
  );
}
```

### Karaoke Effect

```tsx
import { useCurrentFrame, interpolate } from 'remotion';

interface KaraokeCaptionProps {
  text: string;
  startTime: number;
  endTime: number;
  words: WordTiming[];
}

interface WordTiming {
  word: string;
  startTime: number;
  endTime: number;
}

export function KaraokeCaption({ text, startTime, endTime, words }: KaraokeCaptionProps) {
  const frame = useCurrentFrame();
  const fps = 30;
  const currentFrame = frame + startTime * fps;

  return (
    <div style={{ fontFamily: 'NotoSansCJK', fontSize: '24px' }}>
      {words.map((word, index) => {
        const wordStart = word.startTime * fps;
        const wordEnd = word.endTime * fps;
        const isActive = currentFrame >= wordStart && currentFrame <= wordEnd;
        return (
          <span
            key={index}
            style={{
              color: isActive ? '#FFD700' : '#888888',
              transition: 'color 0.1s',
            }}
          >
            {word.word}{' '}
          </span>
        );
      })}
    </div>
  );
}
```

## Font Loading

### Font Manifest

```typescript
interface FontManifest {
  fonts: FontAsset[];
}

interface FontAsset {
  family: string;
  weight: number;
  style: string;
  url: string;           // CDN or storage URL
  format: 'woff2' | 'ttf';
  version: string;
}
```

### Font Loader Component

```tsx
import { useVideoConfig } from 'remotion';

export function FontLoader({ fonts }: { fonts: FontAsset[] }) {
  const { fps } = useVideoConfig();

  return (
    <style>
      {fonts.map(font => `
        @font-face {
          font-family: '${font.family}';
          src: url('${font.url}') format('${font.format}');
          font-weight: ${font.weight};
          font-style: ${font.style};
        }
      `).join('\n')}
    </style>
  );
}
```

## Preview vs Render Consistency

### What Must Match

| Aspect | Preview (Frontend) | Render (Backend) |
|--------|-------------------|------------------|
| Composition | MainComposition.tsx | MainComposition.tsx |
| inputProps | From Editor State | From RenderJob |
| Font assets | Same FontManifest | Same FontManifest |
| Template version | Same | Same |
| Effect version | Same | Same |
| Resolution | Canvas size | Output resolution |
| FPS | Timeline FPS | Output FPS |

### Consistency Guarantee

```
┌─────────────────────────────────────────────────┐
│              Consistency Model                   │
│                                                  │
│  Editor State                                    │
│      │                                           │
│      ▼                                           │
│  RenderJob (Schema Contract)                     │
│      │                                           │
│      ├──► PreviewProps ──► RemotionPlayer        │
│      │                    (Frontend)             │
│      │                                           │
│      └──► Backend API ──► RemotionRenderProvider │
│                           (Server)               │
│                                                  │
│  Same Composition + Same Props = Same Output     │
└─────────────────────────────────────────────────┘
```
