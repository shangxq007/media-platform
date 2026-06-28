# ADR-023: Declarative Plugin Package Manifest and Trust Model

## Status

Accepted

## Context

The platform has established:
- Template System (ADR-022): TemplateDefinition, Atomic profiles (Caption, Watermark), CompositeTemplate
- Workflow System: WorkflowDefinition, WorkflowStep(APPLY_TEMPLATE), WorkflowDryRunPlanner
- Timeline Diff System: TimelineDiff/TimelinePatch vocabulary, bridge types
- PLAN_BASED render as default, FFmpeg/libass as production baseline

Future platform extensibility requires a controlled plugin model. Plugin packages must provide TemplateDefinitions, CompositeTemplateDefinitions, WorkflowDefinitions, UI schemas, examples, and validation rules without exposing executable code, provider internals, or storage internals.

Early plugin support must be declarative-only to ensure safety.

## Decision

### PluginPackage

A `PluginPackage` is a versioned, self-describing package that provides declarative extensions to the Template, Workflow, and UI systems. Plugin packages are internal platform artifacts, not public marketplace products in v0.

### PluginPackageManifest

Every PluginPackage must include a `PluginPackageManifest` (JSON or YAML) describing its identity, exports, permissions, trust level, capabilities, compatibility, and validation metadata.

### Plugin Package Types

| Type | Purpose |
|------|---------|
| TEMPLATE_PACK | Exports TemplateDefinition(s) |
| COMPOSITE_TEMPLATE_PACK | Exports CompositeTemplateDefinition(s) |
| WORKFLOW_PACK | Exports WorkflowDefinition(s) |
| UI_SCHEMA_PACK | Exports UI/form schema metadata |
| VALIDATION_RULE_PACK | Exports declarative validation rules |
| TRUSTED_CODE_EXTENSION | Future: executable code (requires separate ADR) |

### Manifest Schema v0

```yaml
pluginId: string (required, unique, stable)
version: string (required, semantic version)
displayName: string
description: string
vendor: string
license: string
packageType: TEMPLATE_PACK | COMPOSITE_TEMPLATE_PACK | WORKFLOW_PACK | UI_SCHEMA_PACK | VALIDATION_RULE_PACK | TRUSTED_CODE_EXTENSION
trustLevel: DECLARATIVE_COMMUNITY | TENANT_APPROVED_DECLARATIVE | PLATFORM_BUNDLED | TRUSTED_SIGNED_CODE
minimumPlatformVersion: string
exports:
  - exportType: TEMPLATE_DEFINITION | COMPOSITE_TEMPLATE_DEFINITION | WORKFLOW_DEFINITION | UI_FORM_SCHEMA | EXAMPLE_REQUEST | PREVIEW_METADATA | VALIDATION_RULE | CAPABILITY_DECLARATION
    name: string
    content: object (inline definition)
permissions:
  network: false
  filesystem: false
  process: false
  environment: false
  providerSelection: false
  storageInternals: false
  signedUrl: false
  secretAccess: false
  externalServiceCall: false
  codeExecution: false
capabilities: [string]
compatibility:
  minPlatformVersion: string
  maxPlatformVersion: string
validation:
  dryRunRequired: true
  schemaVersion: string
examples:
  - name: string
    request: object
safeMetadata: Map<String, String>
```

### Trust Levels

| Level | Description | Permissions |
|-------|-------------|-------------|
| DECLARATIVE_COMMUNITY | Community-contributed, declarative only | All dangerous permissions denied |
| TENANT_APPROVED_DECLARATIVE | Tenant-reviewed, declarative only | All dangerous permissions denied |
| PLATFORM_BUNDLED | Shipped with platform | Limited safe permissions |
| TRUSTED_SIGNED_CODE | Signed executable code (future) | Requires separate ADR |

### Permission Model

All permissions default to `false`. Declarative plugins cannot enable dangerous permissions:
- `network`, `filesystem`, `process`, `environment` — always false for declarative
- `providerSelection` — always false (providers hidden behind PLAN_BASED)
- `storageInternals`, `signedUrl`, `secretAccess` — always false
- `codeExecution` — always false for declarative; future for TRUSTED_SIGNED_CODE only

### Declarative Sandbox

Declarative plugin packages operate in a sandbox where:
- **Allowed:** TemplateDefinition JSON/YAML, CompositeTemplateDefinition, WorkflowDefinition, UI schema, examples, preview metadata, validation rules, capability declarations, safe strings/numbers/parameter schemas
- **Forbidden:** JavaScript, Python, Java bytecode, WASM, shell commands, FFmpeg commands, Remotion projects, Node/npm/npx, network calls, filesystem access, environment variables, secrets, provider selection, storage references, signed URLs, local paths, executable binding expressions

### Exports

| Export Type | Platform Model | Safety |
|------------|---------------|--------|
| TEMPLATE_DEFINITION | TemplateDefinition | Provider-neutral, no commands |
| COMPOSITE_TEMPLATE_DEFINITION | CompositeTemplateDefinition | Not WorkflowDefinition, inert bindings |
| WORKFLOW_DEFINITION | WorkflowDefinition | Semantic DAG, not execution |
| UI_FORM_SCHEMA | UI metadata | No code |
| EXAMPLE_REQUEST | Example data | Safe strings only |
| PREVIEW_METADATA | Preview info | No storage internals |
| VALIDATION_RULE | Declarative rules | No executable logic |
| CAPABILITY_DECLARATION | Capability claims | No provider binding |

### Validation Rules

- pluginId required, unique, stable
- version required, semantic
- packageType required
- trustLevel required
- exports non-empty
- dangerous permissions denied for declarative plugins
- no executable entrypoints for declarative plugins
- no local paths, signed URLs, provider names, storage internals, secrets
- all exported definitions validated against platform domain schemas
- WorkflowDefinitions must pass WorkflowDryRunPlanner

### Template Integration

- PluginPackage may provide TemplateDefinition
- TemplateDefinition must remain provider-neutral
- TemplateDefinition may declare capabilities, not select providers
- TemplateDefinition must not contain commands or storage internals

### Composite Template Integration

- PluginPackage may provide CompositeTemplateDefinition
- CompositeTemplateDefinition composes atomic templates, is not WorkflowDefinition
- Target/parameter bindings are inert data, not executable scripts
- Composite expansion is dry-run/semantic unless future runtime enables more

### Workflow Integration

- PluginPackage may provide WorkflowDefinition
- WorkflowDefinition is semantic workflow DAG, not render execution DAG
- May include APPLY_TEMPLATE steps
- Must pass WorkflowDryRunPlanner before activation
- Must not execute during import

### Timeline Diff Integration

- Plugin-provided definitions may affect TimelineDiff in future
- TemplateApplicationDiff, CompositeTemplateDiff, WorkflowApplyTemplateStepDiff provide bridge vocabulary
- Plugin packages do not compute or apply diffs

### GitOps Promotion Model (Future)

Future flow:
1. Plugin package source repo
2. Schema validation
3. Security validation
4. Dry-run validation
5. Review approval
6. Staging activation
7. Production promotion
8. Rollback, version lock, audit

No GitOps implementation in P2P.0.

### Runtime Boundaries

- Plugin packages provide intent, not execution authority
- Plugin output compiles into platform-owned semantic models
- Plugins must not bypass ProductRuntime, StorageRuntime, Timeline, PLAN_BASED, ProviderBinding, or execution policy
- Plugins must not select providerName/providerType/backendName
- Plugins must not create FFmpeg commands, Remotion commands, shell commands, local paths, signed URLs
- Plugins must not embed secrets

### Security Boundaries

- All plugin packages validated before acceptance
- All dangerous permissions denied for declarative plugins
- All plugin imports validated
- All plugin activation auditable
- No executable code in declarative plugins
- TRUSTED_SIGNED_CODE requires separate ADR

## Consequences

- Plugin extensibility begins safely with declarative-only packages
- Template/Workflow system can be extended by internal and future external contributors
- Provider/backend/storage boundaries remain enforced
- No security risk from executable code in v0

## Alternatives Considered

- **Executable code plugins now:** Rejected — too much security risk without sandbox infrastructure
- **Python/JS user plugins:** Rejected — arbitrary code execution not safe
- **No plugin model:** Rejected — blocks future extensibility
- **Open plugin marketplace:** Rejected — too early, needs trust model first

## Non-goals

- Plugin runtime
- Plugin registry
- Plugin loader
- Plugin execution engine
- User-uploaded JavaScript/Python plugins
- User-uploaded Remotion projects
- User-uploaded FFmpeg filtergraph plugins
- Plugin-provided shell commands
- Plugin direct StorageRuntime/ProductRuntime access
- Plugin direct ProviderBinding control
- Plugin direct RenderExecutionPlan creation
- Plugin direct signed URL creation
- Plugin direct external network calls
- Plugin direct environment variable access
- Plugin direct filesystem access
- Plugin bypass of WorkflowDryRunPlanner
- Plugin bypass of PLAN_BASED render path

## Follow-up Work

1. Plugin package validator implementation
2. Plugin registry design
3. GitOps promotion for plugin packages
4. TRUSTED_SIGNED_CODE ADR
5. Plugin runtime sandbox (WASM or equivalent)
6. Public plugin API design
7. Plugin marketplace design
