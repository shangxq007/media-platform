---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-006: BMF Integration Architecture

## Context

BMF is both an execution engine and a media processing framework. It must fit into the frozen platform architecture without creating new runtimes or registries.

## Decision

BMF integrates as TWO platform components:
1. BmfExecutionBackend — implements ExecutionBackend for graph-based media execution
2. BmfProviderExtension — implements ProviderExtensionSPI for media capability registration

This is the HYBRID approach: BMF as execution location + BMF as media provider.

## Consequences

- BMF operators are internal to BMF — NOT platform concepts
- AI providers (Whisper, Vision, OCR) compose BMF graphs via BmfExecutionBackend
- OpenCue replaces BmfExecutionBackend without touching providers
- No new runtime or registry needed

## Alternatives Rejected

- Option A (ExecutionBackend only): loses graph composition and operator catalog
- Option B (ProviderPlugin only): always runs as subprocess, no GPU optimization

## Migration

Sprint 046: BmfExecutionBackend implementation
Sprint 047: BmfProviderExtension + operator catalog
Sprint 048: OpenCueExecutionBackend
