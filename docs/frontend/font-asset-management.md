# Font Asset Management

## Overview

Font asset management ensures that fonts used in video editing are consistent between frontend preview and backend render. Fonts are never dependent on system fonts.

## Design Principles

1. **No system font dependency**: All fonts are managed through FontManifest/FontAsset
2. **Same fonts everywhere**: Frontend preview and backend render use the same font assets
3. **Versioned**: Font assets have versions for cache busting and consistency
4. **Centralized**: Font manifest is the single source of truth
5. **Lazy loading**: Fonts are loaded on demand

## Font Manifest

```typescript
interface FontManifest {
  version: string;
  fonts: FontAsset[];
  lastUpdated: string;
}

interface FontAsset {
  id: string;
  family: string;
  weight: number;
  style: 'normal' | 'italic';
  url: string;              // CDN or storage URL
  format: 'woff2' | 'ttf' | 'otf';
  version: string;
  fileSize: number;
  checksum: string;         // SHA-256 for integrity verification
  license: string;
  supportedLanguages: string[];
}

interface FontUsage {
  fontFamily: string;
  usedInCaptions: boolean;
  usedInTemplates: boolean;
  usedInTitles: boolean;
}
```

## Font Registry

```typescript
class FontRegistry {
  private manifest: FontManifest | null = null;
  private loadedFonts: Map<string, FontFace> = new Map();

  async loadManifest(url: string): Promise<FontManifest> {
    const response = await fetch(url);
    this.manifest = await response.json();
    return this.manifest;
  }

  async loadFont(asset: FontAsset): Promise<void> {
    if (this.loadedFonts.has(asset.id)) return;

    const fontFace = new FontFace(asset.family, `url(${asset.url})`, {
      weight: String(asset.weight),
      style: asset.style,
    });

    const loaded = await fontFace.load();
    document.fonts.add(loaded);
    this.loadedFonts.set(asset.id, loaded);
  }

  async loadAll(): Promise<void> {
    if (!this.manifest) return;
    await Promise.all(this.manifest.fonts.map(f => this.loadFont(f)));
  }

  isLoaded(fontFamily: string, weight: number): boolean {
    return Array.from(this.loadedFonts.values()).some(
      f => f.family === fontFamily && f.weight === String(weight)
    );
  }

  getAvailableFonts(): FontAsset[] {
    return this.manifest?.fonts || [];
  }

  getFontByFamily(family: string): FontAsset[] {
    return this.manifest?.fonts.filter(f => f.family === family) || [];
  }
}

export const fontRegistry = new FontRegistry();
```

## Font Loader Component (Remotion)

```tsx
import { useEffect, useState } from 'react';
import { useVideoConfig } from 'remotion';

interface FontLoaderProps {
  fonts: FontAsset[];
  onLoaded?: () => void;
  onError?: (error: Error) => void;
}

export function FontLoader({ fonts, onLoaded, onError }: FontLoaderProps) {
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadFonts() {
      try {
        const fontFaces = fonts.map(
          font =>
            new FontFace(font.family, `url(${font.url})`, {
              weight: String(font.weight),
              style: font.style,
            })
        );

        const loaded = await Promise.all(fontFaces.map(f => f.load()));

        if (cancelled) return;

        loaded.forEach(f => {
          document.fonts.add(f);
        });

        setLoaded(true);
        onLoaded?.();
      } catch (err) {
        if (cancelled) return;
        const error = err instanceof Error ? err : new Error(String(err));
        setError(error);
        onError?.(error);
      }
    }

    loadFonts();
    return () => { cancelled = true; };
  }, [fonts, onLoaded, onError]);

  if (error) {
    return (
      <div style={{ color: 'red', padding: '16px' }}>
        Font loading failed: {error.message}
      </div>
    );
  }

  if (!loaded) {
    return (
      <div style={{ padding: '16px', opacity: 0.5 }}>
        Loading fonts...
      </div>
    );
  }

  return null;
}
```

## Font Asset API

```typescript
// Font Manifest API
async function fetchFontManifest(projectId: string): Promise<FontManifest> {
  const response = await fetch(`/api/v1/projects/${projectId}/font-manifest`);
  return response.json();
}

async function uploadFontAsset(
  projectId: string,
  file: File,
  metadata: Partial<FontAsset>
): Promise<FontAsset> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('metadata', JSON.stringify(metadata));

  const response = await fetch(`/api/v1/projects/${projectId}/fonts`, {
    method: 'POST',
    body: formData,
  });
  return response.json();
}

async function deleteFontAsset(projectId: string, fontId: string): Promise<void> {
  await fetch(`/api/v1/projects/${projectId}/fonts/${fontId}`, {
    method: 'DELETE',
  });
}
```

## Font Consistency Guarantee

### How It Works

```
┌─────────────────────────────────────────────────────────┐
│                 Font Consistency Model                   │
│                                                          │
│  1. Font Manifest is created/updated                     │
│     └── Stored in project storage                        │
│                                                          │
│  2. Frontend loads Font Manifest                         │
│     └── FontRegistry loads all fonts                     │
│     └── Remotion Player uses loaded fonts                │
│                                                          │
│  3. RenderJob includes font references                  │
│     └── fontFamily: "NotoSansCJK"                        │
│     └── fontAssetUri: "s3://fonts/NotoSansCJK.ttf"      │
│                                                          │
│  4. Backend Remotion Renderer loads same fonts           │
│     └── Downloads from fontAssetUri                      │
│     └── Uses same fontFamily                             │
│                                                          │
│  5. Same Composition + Same Fonts = Same Output          │
└─────────────────────────────────────────────────────────┘
```

### Font Resolution Flow

```typescript
function resolveFontForRenderJob(
  fontFamily: string,
  fontManifest: FontManifest
): FontAsset | null {
  const font = fontManifest.fonts.find(f => f.family === fontFamily);
  if (!font) {
    console.warn(`Font "${fontFamily}" not found in manifest, falling back to system font`);
    return null;
  }
  return font;
}

function validateFontAvailability(
  captions: Caption[],
  fontManifest: FontManifest
): { available: string[]; missing: string[] } {
  const fontFamilies = new Set(captions.map(c => c.style.fontFamily));
  const available: string[] = [];
  const missing: string[] = [];

  for (const family of fontFamilies) {
    const found = fontManifest.fonts.some(f => f.family === family);
    if (found) {
      available.push(family);
    } else {
      missing.push(family);
    }
  }

  return { available, missing };
}
```

## Font Fallback Strategy

```typescript
interface FontFallbackConfig {
  primary: string;
  fallbacks: string[];
  systemFallback: string;
}

const defaultFontFallbacks: Record<string, FontFallbackConfig> = {
  'NotoSansCJK': {
    primary: 'NotoSansCJK',
    fallbacks: ['NotoSansSC', 'NotoSansJP', 'NotoSansKR'],
    systemFallback: 'sans-serif',
  },
  'NotoSans': {
    primary: 'NotoSans',
    fallbacks: ['Roboto', 'Arial'],
    systemFallback: 'sans-serif',
  },
};

function getFontStack(fontFamily: string): string[] {
  const config = defaultFontFallbacks[fontFamily];
  if (config) {
    return [config.primary, ...config.fallbacks, config.systemFallback];
  }
  return [fontFamily, 'sans-serif'];
}
```

## Font License Tracking

```typescript
interface FontLicense {
  fontId: string;
  license: string;           // 'OFL', 'Apache-2.0', 'Proprietary', etc.
  commercialUse: boolean;
  modification: boolean;
  redistribution: boolean;
  attribution: string;
  licenseUrl: string;
}

function checkFontLicense(fontId: string, usage: 'preview' | 'render' | 'export'): boolean {
  // Implementation: check if the font license allows the specified usage
  return true;
}
```

## Font Store (Zustand)

```typescript
import { create } from 'zustand';

interface FontStore {
  manifest: FontManifest | null;
  loadedFonts: Set<string>;
  loading: boolean;
  error: string | null;

  // Actions
  loadManifest: (projectId: string) => Promise<void>;
  loadFont: (fontId: string) => Promise<void>;
  loadAllFonts: () => Promise<void>;
  unloadFont: (fontId: string) => void;
  isFontLoaded: (fontId: string) => boolean;
  getAvailableFonts: () => FontAsset[];
}

export const useFontStore = create<FontStore>((set, get) => ({
  manifest: null,
  loadedFonts: new Set(),
  loading: false,
  error: null,

  loadManifest: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const manifest = await fetchFontManifest(projectId);
      set({ manifest, loading: false });
    } catch (err) {
      set({ error: (err as Error).message, loading: false });
    }
  },

  loadFont: async (fontId) => {
    const { manifest } = get();
    if (!manifest) return;

    const font = manifest.fonts.find(f => f.id === fontId);
    if (!font) return;

    try {
      const fontFace = new FontFace(font.family, `url(${font.url})`, {
        weight: String(font.weight),
        style: font.style,
      });
      await fontFace.load();
      document.fonts.add(fontFace);
      set(state => ({
        loadedFonts: new Set([...state.loadedFonts, fontId]),
      }));
    } catch (err) {
      console.error(`Failed to load font ${font.family}:`, err);
    }
  },

  loadAllFonts: async () => {
    const { manifest } = get();
    if (!manifest) return;
    await Promise.all(manifest.fonts.map(f => get().loadFont(f.id)));
  },

  unloadFont: (fontId) => {
    set(state => {
      const next = new Set(state.loadedFonts);
      next.delete(fontId);
      return { loadedFonts: next };
    });
  },

  isFontLoaded: (fontId) => get().loadedFonts.has(fontId),

  getAvailableFonts: () => get().manifest?.fonts || [],
}));
```

## Font Preview Component

```tsx
function FontPreview({ font }: { font: FontAsset }) {
  const { isFontLoaded, loadFont } = useFontStore();

  useEffect(() => {
    if (!isFontLoaded(font.id)) {
      loadFont(font.id);
    }
  }, [font.id, isFontLoaded, loadFont]);

  return (
    <div
      style={{
        fontFamily: font.family,
        fontWeight: font.weight,
        fontStyle: font.style,
        fontSize: '24px',
        padding: '16px',
        border: '1px solid #ccc',
        borderRadius: '8px',
      }}
    >
      The quick brown fox jumps over the lazy dog
      <div style={{ fontSize: '12px', color: '#666', marginTop: '8px' }}>
        {font.family} {font.weight} {font.style}
      </div>
    </div>
  );
}
```
