# Frontend Effects Panel — Operation Manual

> **Last updated**: 2026-05-11

## Overview

The Effects Panel in the Timeline Editor allows users to browse, drag, and apply effects to clips and tracks.

## Effect Packs

### Built-in Pack
The `builtin-core` pack includes:
- **Transitions**: Fade In, Fade Out, Cross Dissolve
- **Video Filters**: Blur, Vignette
- **Text**: Subtitle Burn-in

### Custom Effect Packs
Administrators can provide custom effect packs via `effect-pack.json`:

```json
{
  "packId": "custom-pack",
  "version": "1.0.0",
  "name": "Custom Effects",
  "effects": [
    {
      "effectKey": "video.blur",
      "displayName": "Blur",
      "category": "video",
      "parameterSchema": {
        "radius": { "type": "float", "defaultValue": 2.0, "min": 0.1, "max": 10.0 }
      },
      "defaultValues": { "radius": 2.0 },
      "providerMappings": ["ofx", "javacv"],
      "allowedTiers": ["FREE", "PRO", "TEAM", "ENTERPRISE"]
    }
  ]
}
```

## Applying Effects

1. Select effect category tab (transition/video/audio/text)
2. Drag effect onto a clip in the timeline
3. Edit parameters in the panel below
4. Effects appear as badges on clips

## OTIO Metadata

Effects are written to OTIO timeline metadata:

```json
{
  "tracks": [{
    "children": [{
      "effects": [{
        "id": "ce_...",
        "effectKey": "video.fade_in",
        "packId": "builtin-core",
        "packVersion": "2.0.0",
        "providerPreference": ["javacv"],
        "parameters": { "duration": 1.0 }
      }]
    }]
  }]
}
```

## Tier Restrictions

| Tier | Available Effects |
|------|------------------|
| FREE | Fade, Blur, Subtitle |
| PRO | + Vignette, Text Overlay |
| TEAM | + Chromatic, Overlay, PiP |
| ENTERPRISE | All stable effects |
| EXPERIMENTAL | All + experimental |

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Delete | Remove selected effect |
| Ctrl+Z | Undo effect change |
| Ctrl+Y | Redo effect change |
