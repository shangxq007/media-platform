# Effect Pack Schema

> **Last updated**: 2026-05-11
> **Schema Version**: 2.0.0

## Top-Level Structure

```json
{
  "packId": "string (unique identifier)",
  "version": "semver",
  "name": "string",
  "description": "string",
  "author": "string",
  "effects": ["EffectDefinition" ],
  "compatibility": "string (e.g. '2.0')",
  "allowedTiers": ["FREE" | "PRO" | "TEAM" | "ENTERPRISE"]
}
```

## Effect Definition

```json
{
  "effectKey": "string (e.g. 'video.fade_in')",
  "displayName": "string",
  "category": "transition | video | audio | text",
  "description": "string",
  "parameterSchema": {
    "paramName": {
      "type": "int | float | string | boolean | color",
      "defaultValue": "any",
      "min": "number (optional)",
      "max": "number (optional)",
      "description": "string"
    }
  },
  "defaultValues": { "paramName": "value" },
  "providerMappings": ["javacv" | "ofx" | "mlt" | "gpac"],
  "allowedTiers": ["FREE" | "PRO" | "TEAM" | "ENTERPRISE"],
  "thumbnailUrl": "string (optional)"
}
```

## Effect Key Naming Convention

| Prefix | Category | Examples |
|--------|----------|----------|
| `video.` | Video filters/transitions | `video.fade_in`, `video.blur` |
| `text.` | Text/subtitle effects | `text.subtitle_burn_in` |
| `audio.` | Audio effects | `audio.volume` |

## v1 → v2 Migration

| v1 Field | v2 Field |
|----------|----------|
| `effectId` | `effectKey` |
| `parameters` | `parameterSchema` + `defaultValues` |
| `renderProviders` | `providerMappings` |
| N/A | `allowedTiers` |
| N/A | `compatibility` |
