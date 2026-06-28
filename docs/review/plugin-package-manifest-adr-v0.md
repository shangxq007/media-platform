# Plugin Package Manifest ADR Review (P2P.0)

## Summary

ADR-023 defines the declarative plugin package manifest, trust model, and sandbox architecture. Early plugin packages are declarative-only. No executable code, no runtime, no registry.

## ADR Decision

- Plugin packages are declarative-only in v0
- May export: TemplateDefinition, CompositeTemplateDefinition, WorkflowDefinition, UI schema, examples, preview metadata, validation rules, capability declarations
- May NOT: execute code, select providers, access storage, create commands, embed secrets
- Trust levels: DECLARATIVE_COMMUNITY, TENANT_APPROVED_DECLARATIVE, PLATFORM_BUNDLED, TRUSTED_SIGNED_CODE (future)
- All dangerous permissions denied for declarative plugins

## Manifest Schema v0

16 fields: pluginId, version, displayName, description, vendor, license, packageType, trustLevel, minimumPlatformVersion, exports, permissions, capabilities, compatibility, validation, examples, safeMetadata

## Example Manifests

1. Caption Template Pack — exports TemplateDefinition + UI schema + example
2. Social Short Video Composite Template Pack — exports CompositeTemplateDefinition
3. Workflow Pack — exports WorkflowDefinition with APPLY_TEMPLATE steps
4. Rejected: dangerous permissions

## Template/Composite/Workflow Integration

- TemplateDefinition: provider-neutral, no commands, may declare capabilities
- CompositeTemplateDefinition: composes atomic templates, inert bindings
- WorkflowDefinition: semantic DAG, must pass dry-run planner

## Security Boundaries

- All permissions default false
- Declarative plugins cannot enable dangerous permissions
- No executable code
- No network/filesystem/process access
- No provider selection
- No storage internals

## Files Changed

| File | Type |
|------|------|
| `docs/architecture/adr/ADR-023-declarative-plugin-package-manifest.md` | NEW |
| `docs/architecture/plugin-package-manifest.md` | NEW |
| `docs/architecture/plugin-trust-model.md` | NEW |
| `docs/architecture/declarative-plugin-sandbox.md` | NEW |
| `docs/review/plugin-package-manifest-adr-v0.md` | NEW |

## ADR Status

**ACCEPTED**
