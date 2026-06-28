# Plugin Trust Model

## Purpose

Define trust levels, permissions, and security boundaries for plugin packages.

## Threat Model

| Threat | Mitigation |
|--------|-----------|
| Arbitrary code execution | Declarative-only plugins, no executable code |
| Provider manipulation | Plugins cannot select providers |
| Storage exfiltration | No storage internals exposed |
| Secret leakage | No secret access for declarative plugins |
| Command injection | No shell/FFmpeg/Remotion commands |
| Network exfiltration | No network access for declarative plugins |
| Filesystem access | No filesystem access for declarative plugins |
| Workflow bypass | WorkflowDefinitions must pass dry-run |

## Trust Levels

### DECLARATIVE_COMMUNITY
- Community-contributed, declarative only
- All dangerous permissions denied
- Must pass schema validation
- Must pass dry-run validation

### TENANT_APPROVED_DECLARATIVE
- Tenant-reviewed, declarative only
- All dangerous permissions denied
- Requires tenant approval before activation

### PLATFORM_BUNDLED
- Shipped with platform
- Limited safe permissions
- Maintained by platform team

### TRUSTED_SIGNED_CODE
- Future: signed executable code
- Requires separate ADR
- Requires sandbox infrastructure
- Not implemented in v0

## Permission Model

| Permission | Default | Declarative | Platform Bundled | Trusted Code |
|-----------|---------|------------|-----------------|-------------|
| network | false | ❌ Denied | ❌ Denied | Future ADR |
| filesystem | false | ❌ Denied | ❌ Denied | Future ADR |
| process | false | ❌ Denied | ❌ Denied | Future ADR |
| environment | false | ❌ Denied | ❌ Denied | Future ADR |
| providerSelection | false | ❌ Denied | ❌ Denied | ❌ Always denied |
| storageInternals | false | ❌ Denied | ❌ Denied | ❌ Always denied |
| signedUrl | false | ❌ Denied | ❌ Denied | ❌ Always denied |
| secretAccess | false | ❌ Denied | ❌ Denied | Future ADR |
| externalServiceCall | false | ❌ Denied | ❌ Denied | Future ADR |
| codeExecution | false | ❌ Denied | ❌ Denied | Future ADR |

## Signing and Verification (Future)

- TRUSTED_SIGNED_CODE packages must be signed
- Platform verifies signature before loading
- Unsigned code packages rejected
- Requires separate ADR

## Version Locking

- Plugin packages are version-locked at activation time
- No automatic version upgrades
- Version changes require re-validation and approval

## Audit Requirements

- All plugin package imports auditable
- All plugin activation events auditable
- All permission requests auditable
- Audit events internal only

## Rollback Requirements

- Plugin deactivation must be instant
- No data migration required for rollback
- Plugin state must be cleanly separable

## Tenant Isolation

- Tenant-approved plugins scoped to tenant
- Platform-bundled plugins shared
- No cross-tenant plugin leakage

## Review and Approval Process (Future)

1. Plugin package submitted
2. Schema validation
3. Security validation
4. Dry-run validation
5. Review approval
6. Staging activation
7. Production promotion

## What Is Explicitly Forbidden

- User-uploaded JavaScript/Python code
- User-uploaded Remotion projects
- User-uploaded FFmpeg filtergraphs
- Plugin shell commands
- Plugin filesystem access
- Plugin network calls
- Plugin environment variable access
- Plugin secret access
- Plugin provider selection
- Plugin storage reference creation
- Plugin signed URL creation
- Plugin direct ProductRuntime mutation
- Plugin direct StorageRuntime mutation
- Plugin direct ProviderBinding control
- Plugin direct RenderExecutionPlan creation
- Plugin bypass of WorkflowDryRunPlanner
- Plugin bypass of PLAN_BASED render path

## Future Runtime Considerations

- WASM sandbox for trusted code
- Process isolation for executable plugins
- Network sandboxing
- Filesystem sandboxing
- Resource limits (CPU, memory, time)
