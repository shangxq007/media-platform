# Render Worker Architecture

> **Last updated**: 2026-05-11
> **Status**: Design document — not yet implemented

## Overview

The Render Worker is a separate service that handles video rendering jobs asynchronously from the main API server. This document describes the architecture and deployment strategy.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Server (platform-app)                    │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │ RenderJob    │  │ ExportPolicy    │  │ RenderProvider   │   │
│  │ Controller   │──│ Service         │──│ Router           │   │
│  └──────────────┘  └─────────────────┘  └──────────────────┘   │
│         │                                       │               │
│         │         ┌─────────────────┐           │               │
│         └────────▶│ Outbox Event    │◀──────────┘               │
│                   │ Module          │                           │
│                   └─────────────────┘                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ Message Queue (future: Kafka/RabbitMQ)
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                     Render Worker Service                        │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │ Job Consumer │  │ Provider        │  │ Quality          │   │
│  │              │──│ Registry        │──│ Check Service    │   │
│  └──────────────┘  └─────────────────┘  └──────────────────┘   │
│         │                  │                     │              │
│         │         ┌────────┴────────┐            │              │
│         │         │                 │            │              │
│         ▼         ▼                 ▼            ▼              │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────┐      │
│  │ JavaCV   │ │ OFX      │ │ Future:      │ │ Media    │      │
│  │ Provider │ │ Provider │ │ GStreamer/MLT│ │ Probe    │      │
│  └──────────┘ └──────────┘ └──────────────┘ └──────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

## Provider Registry

The `RenderProviderRegistry` manages all available render providers:

| Provider | Key | Capabilities | Experimental |
|----------|-----|--------------|--------------|
| JavaCV | `javacv` | H.264, AAC, clipping, basic filters | No |
| OFX | `ofx` | Advanced effects, transitions, compositing | No |
| Mock | `mock` | Testing only | Yes |

## Export Tiers

| Tier | Level | Max Resolution | Watermark | OFX Effects |
|------|-------|----------------|-----------|-------------|
| FREE | 1 | 720p | Yes | Basic only |
| PRO | 2 | 1080p | No | Yes |
| TEAM | 3 | 4K | No | Yes |
| ENTERPRISE | 4 | 4K | No | All |
| EXPERIMENTAL | 5 | 4K | No | All + experimental |

## Effect Standard Mapping

Effects use standardized keys (e.g., `video.fade_in`, `text.subtitle_burn_in`) that are mapped to provider-specific implementations by the `EffectMappingService`.

## Docker Deployment

### Current (Monolithic)

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    # Contains all providers
```

### Future (Split)

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    # API only, no render providers

  render-worker-javacv:
    build:
      context: .
      dockerfile: docker/render-worker-javacv.Dockerfile
    # JavaCV + FFmpeg

  render-worker-ofx:
    build:
      context: .
      dockerfile: docker/render-worker-ofx.Dockerfile
    # OFX plugins
```
