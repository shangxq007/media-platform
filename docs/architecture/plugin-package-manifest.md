# Plugin Package Manifest

## Purpose

Define the manifest schema for declarative plugin packages that extend the Template, Workflow, and UI systems.

## Non-goals

- Executable code plugins
- Plugin runtime/loader
- Public marketplace
- User-uploaded arbitrary code

## PluginPackage

A versioned, self-describing package providing declarative extensions. Internal platform artifact.

## PluginPackageManifest

Required manifest fields:

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| pluginId | string | Yes | Unique, stable identifier |
| version | string | Yes | Semantic version |
| displayName | string | No | Human-readable name |
| description | string | No | Package description |
| vendor | string | No | Package vendor |
| license | string | No | License identifier |
| packageType | enum | Yes | TEMPLATE_PACK, COMPOSITE_TEMPLATE_PACK, etc. |
| trustLevel | enum | Yes | DECLARATIVE_COMMUNITY, TENANT_APPROVED_DECLARATIVE, etc. |
| minimumPlatformVersion | string | No | Minimum platform version |
| exports | list | Yes | Non-empty list of exports |
| permissions | map | No | All default to false |
| capabilities | list | No | Capability declarations |
| compatibility | object | No | Platform version compatibility |
| validation | object | No | Validation metadata |
| examples | list | No | Example requests |
| safeMetadata | map | No | Safe string metadata only |

## Package Types

| Type | Purpose |
|------|---------|
| TEMPLATE_PACK | Exports TemplateDefinition(s) |
| COMPOSITE_TEMPLATE_PACK | Exports CompositeTemplateDefinition(s) |
| WORKFLOW_PACK | Exports WorkflowDefinition(s) |
| UI_SCHEMA_PACK | Exports UI/form schema metadata |
| VALIDATION_RULE_PACK | Exports declarative validation rules |
| TRUSTED_CODE_EXTENSION | Future: executable code (requires separate ADR) |

## Export Types

| Export Type | Platform Model | Safety |
|------------|---------------|--------|
| TEMPLATE_DEFINITION | TemplateDefinition | Provider-neutral, no commands |
| COMPOSITE_TEMPLATE_DEFINITION | CompositeTemplateDefinition | Inert bindings |
| WORKFLOW_DEFINITION | WorkflowDefinition | Semantic DAG only |
| UI_FORM_SCHEMA | UI metadata | No code |
| EXAMPLE_REQUEST | Example data | Safe strings only |
| PREVIEW_METADATA | Preview info | No storage internals |
| VALIDATION_RULE | Declarative rules | No executable logic |
| CAPABILITY_DECLARATION | Capability claims | No provider binding |

## Validation Rules

1. pluginId required, unique, stable
2. version required, semantic
3. packageType required
4. trustLevel required
5. exports non-empty
6. Dangerous permissions denied for declarative plugins
7. No executable entrypoints for declarative plugins
8. No local paths, signed URLs, provider names, storage internals, secrets
9. All exported definitions validated against platform domain schemas
10. WorkflowDefinitions must pass WorkflowDryRunPlanner

## Example: Caption Template Pack

```yaml
pluginId: com.example.caption-basic-pack
version: 1.0.0
displayName: Basic Caption Template Pack
description: Provides basic caption/subtitle overlay templates
vendor: Media Platform Team
license: internal
packageType: TEMPLATE_PACK
trustLevel: PLATFORM_BUNDLED
minimumPlatformVersion: 1.0.0
exports:
  - exportType: TEMPLATE_DEFINITION
    name: basic-caption
    content:
      id: builtin.caption.basic
      version: 1.0.0
      type: CAPTION
      targetRoles: [MAIN_VIDEO, CAPTION_TRACK]
      operations:
        - operationId: op-add-text-overlay
          type: ADD_TEXT_OVERLAY
          targetRole: CAPTION_TRACK
      requiredCapabilities:
        - capability: TEXT_OVERLAY
          required: true
  - exportType: UI_FORM_SCHEMA
    name: caption-form
    content:
      fields:
        - name: text
          type: string
          required: true
        - name: startMs
          type: number
          required: true
        - name: endMs
          type: number
          required: true
  - exportType: EXAMPLE_REQUEST
    name: basic-caption-example
    content:
      sourceProductId: prod-source-1
      captionSegments:
        - startMs: 0
          endMs: 2500
          text: Hello from Media Platform
permissions:
  network: false
  filesystem: false
  process: false
  codeExecution: false
```

## Example: Social Short Video Composite Template Pack

```yaml
pluginId: com.example.social-short-video-pack
version: 1.0.0
displayName: Social Short Video Composite Template
description: Combines caption and watermark for social media short videos
packageType: COMPOSITE_TEMPLATE_PACK
trustLevel: DECLARATIVE_COMMUNITY
exports:
  - exportType: COMPOSITE_TEMPLATE_DEFINITION
    name: social-short-video
    content:
      id: builtin.social.short-video
      templateId: builtin.social.short-video
      version: 1.0.0
      children:
        - id: caption-child
          childTemplateId: builtin.caption.basic
          childTemplateVersion: 1.0.0
          order: 0
          required: true
        - id: watermark-child
          childTemplateId: builtin.watermark.basic
          childTemplateVersion: 1.0.0
          order: 1
          required: true
      targetBindings:
        - parentRole: MAIN_VIDEO
          childId: caption-child
          childRole: MAIN_VIDEO
          required: true
        - parentRole: MAIN_VIDEO
          childId: watermark-child
          childRole: MAIN_VIDEO
          required: true
      mergePolicy: ORDERED
      conflictPolicy: FAIL_FAST
permissions:
  network: false
  codeExecution: false
```

## Example: Workflow Pack

```yaml
pluginId: com.example.auto-caption-workflow
version: 1.0.0
displayName: Auto Caption Workflow
description: Automated caption workflow with ASR and rendering
packageType: WORKFLOW_PACK
trustLevel: TENANT_APPROVED_DECLARATIVE
exports:
  - exportType: WORKFLOW_DEFINITION
    name: auto-caption-workflow
    content:
      id: auto-caption-workflow
      version: 1.0.0
      steps:
        - id: ingest
          type: INGEST_PRODUCT
        - id: apply-caption
          type: APPLY_TEMPLATE
          dependencies:
            - dependsOnStepId: ingest
          templateApplicationSpec:
            templateId: builtin.caption.basic
            templateVersion: 1.0.0
        - id: render
          type: RENDER_TIMELINE
          dependencies:
            - dependsOnStepId: apply-caption
        - id: deliver
          type: DELIVER_PRODUCT
          dependencies:
            - dependsOnStepId: render
permissions:
  network: false
  codeExecution: false
```

## Rejected Example

```yaml
pluginId: com.example.dangerous-plugin
version: 1.0.0
displayName: Dangerous Plugin
packageType: TEMPLATE_PACK
trustLevel: DECLARATIVE_COMMUNITY
exports:
  - exportType: TEMPLATE_DEFINITION
    name: malicious-template
    content: {}
permissions:
  process: true           # REJECTED: declarative cannot enable
  providerSelection: true  # REJECTED: always denied
  storageInternals: true   # REJECTED: always denied
  signedUrl: true          # REJECTED: always denied
  codeExecution: true      # REJECTED: always denied
```

**Rejection reasons:** Declarative plugins cannot enable process, providerSelection, storageInternals, signedUrl, or codeExecution permissions. These require TRUSTED_SIGNED_CODE (future ADR).

## Future Work

- Plugin package validator implementation
- Plugin registry
- GitOps promotion
- TRUSTED_SIGNED_CODE ADR
- Plugin runtime sandbox
