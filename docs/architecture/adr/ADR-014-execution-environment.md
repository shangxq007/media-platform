---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-014: Execution Environment

## Context

Platform Kernel v1.0 defines ExecutionBackend as the media processing layer. However, future execution targets (OpenCue, Kubernetes, Ray, Cloud Render) are fundamentally different from backends — they provide scheduling, worker selection, container isolation, not media processing.

## Decision

Separate Execution Backend from Execution Environment:

1. Execution Backend = WHAT media processing (BMF, FFmpeg, Remotion)
2. Execution Environment = WHERE/HOW execution occurs (Local, OpenCue, K8s, Ray)

They are orthogonal. BMF may execute on Local or OpenCue. FFmpeg may execute on Local or Kubernetes.

## Consequences

- New platform layer: Environment Layer (between Compilation and Execution)
- Backends unchanged — no openCue code in BMF/FFmpeg providers
- OpenCue integrates as an Environment, not a Backend
- Worker model needed for environment selection (future)

## Migration

Phase 1: OpenCue as Execution Environment for BMF/FFmpeg
Phase 2: Kubernetes Environment
Phase 3: Ray, Cloud Render
