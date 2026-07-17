# Platform Kernel Contract

**Contract ID:** platform-kernel
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED

## PK-001: Platform Positioning
The Media Capability Platform **MUST** be understood as a full-stack media production platform, not merely an editor, render farm, AI application, or FFmpeg wrapper.

## PK-002: Platform Kernel Boundary
The Platform Kernel **MUST** provide stable SPIs for Product, Timeline, RenderJob, and Provider abstractions. Module boundaries **MUST** be respected.

## PK-003: Stable SPI Boundary
Public interfaces **MUST NOT** change without ADR acceptance. Breaking changes **MUST** go through governance review.

## PK-004: Module Ownership
- `render-module`: execution, rendering, providers
- `platform-app`: API, controllers, DTOs, migrations
- `shared-kernel`: common abstractions

## PK-005: Extension Layer
Extension-layer designs (Artifact DAG) **MUST NOT** be treated as core platform requirements.

## Non-Goals
- Platform is NOT merely an editor
- Platform is NOT merely a render farm
- Platform is NOT merely an AI application
- Platform is NOT merely an FFmpeg wrapper

## Change Authority
- USER_EXPLICIT_APPROVAL
- ADR_ACCEPTANCE
