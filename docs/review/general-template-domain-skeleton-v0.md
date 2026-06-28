# General Template Domain Skeleton v0 (P2T.1)

## Purpose

Compile-safe domain foundation for the General Template System accepted in ADR-022. Provides generic template value objects without replacing the existing Caption Template MVP.

## Package Placement

`render-module/.../domain/template/` — generic template domain, separate from caption-specific code.

## Classes Added

### Enums
- `TemplateType` — CAPTION, WATERMARK, BRAND, LAYOUT, TRANSITION, COMPOSITION, CUSTOM
- `TemplateTargetRole` — 13 roles from ADR-022 (MAIN_VIDEO, CAPTION_TRACK, LOGO, etc.)
- `TemplateTargetType` — PRODUCT, TEXT, TIMELINE_TRACK, TIMELINE_CLIP, PARAMETER
- `TemplateOperationType` — 14 operations from ADR-022 (ADD_TEXT_OVERLAY, TRIM, etc.)
- `TemplateApplicationStatus` — SUCCESS, VALIDATION_FAILED, COMPILATION_FAILED, etc.

### Records
- `TemplateDefinitionId` — stable identifier (non-blank validated)
- `TemplateVersion` — semantic version (non-blank validated)
- `TemplateDisplayMetadata` — name, description, iconRef
- `TemplateCapabilityRequirement` — capability code, required flag, constraints
- `TemplateConstraint` — constraint type, value, description
- `TemplateTarget` — role + targetType + targetId + safeMetadata
- `TemplateParameterValue` — typed value (string/int/double/bool)
- `TemplateParameter` — parameter definition (id, name, type, required, default)
- `TemplateOperation` — operationId + type + targetRole + parameters + capabilities
- `TemplateDefinition` — id + version + type + metadata + targetRoles + parameters + operations + constraints + capabilities
- `TemplateApplicationRequest` — projectId + templateId + templateVersion + targets + parameters + safeMetadata
- `TemplateValidationError` — field + code + message
- `TemplateValidationResult` — valid + errors
- `TemplateApplicationResult` — status + validation + safeMessage + warnings

### Interface
- `TemplateApplicationCompiler` — `supports(definition)` + `compile(definition, request)`

## Invariants

- TemplateDefinition requires id, version, type
- TemplateApplicationRequest requires projectId, templateId, non-empty targets
- TemplateTarget requires role, type, non-blank targetId
- TemplateOperation requires type and targetRole
- TemplateCapabilityRequirement requires non-blank capability
- All models are provider-neutral, storage-neutral

## Safety Boundaries

- No provider names in domain models
- No storage internals (bucket/objectKey/signedUrl/path)
- No FFmpeg commands
- No Remotion props
- No raw commands
- No environment variables
- No persistence annotations
- No API DTO annotations

## Relationship to ADR-022

This skeleton implements the domain model layer of the accepted General Template System architecture. Future phases:
- P2T.2: CaptionTemplate mapped as first Template profile
- P2T.3: WatermarkTemplate as second profile
- P2W.0: Workflow semantic model
- P2P.0: Plugin registry

## Relationship to Caption Template MVP

Existing CaptionTemplateRenderRequest/Controller/Service remain unchanged. P2T.2 will map them to TemplateDefinition/TemplateApplicationRequest. No breaking changes.

## Intentionally Not Implemented

- Generic Template API endpoint
- Template persistence
- Plugin runtime
- Workflow runtime
- TemplateApplicationCompiler implementation (interface only)
- Safe delivery resolver
