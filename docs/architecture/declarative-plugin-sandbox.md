# Declarative Plugin Sandbox

## Purpose

Define the declarative-only sandbox boundary for early plugin packages.

## Declarative-only Rule

Early plugin packages are data-only. They provide definitions, schemas, examples, and metadata. They do not contain or execute code.

## Allowed Inputs

- TemplateDefinition JSON/YAML
- CompositeTemplateDefinition JSON/YAML
- WorkflowDefinition JSON/YAML
- UI form schema
- Example requests
- Preview metadata
- Capability declarations
- Validation rules
- Safe strings, numbers, parameter schemas
- TemplateTargetBinding (inert data)
- TemplateParameterBinding (inert data)
- TemplateBindingExpression (inert string, not executed)

## Allowed Outputs

- Platform-owned TemplateDefinition objects
- Platform-owned CompositeTemplateDefinition objects
- Platform-owned WorkflowDefinition objects
- UI schema metadata
- Validation results
- Dry-run plans

## Forbidden Inputs

- JavaScript code
- Python code
- Java bytecode
- WASM modules (future only)
- Shell commands
- FFmpeg commands
- Remotion projects
- Node/npm/npx commands
- Network URLs
- Local filesystem paths
- Environment variables
- Secrets/credentials
- Provider names/selection
- Storage references
- Signed URLs
- Executable binding expressions

## Forbidden Outputs

- FFmpeg commands
- Remotion render commands
- Shell commands
- Provider selection
- Storage mutations
- Product mutations
- Signed URLs
- Local file paths

## TemplateDefinition Sandbox

- Provider-neutral
- No commands
- No storage internals
- May declare capabilities
- May not select providers

## CompositeTemplateDefinition Sandbox

- Composes atomic templates
- Target bindings are inert data
- Parameter bindings are inert data
- Binding expressions are strings, not executed
- Not a WorkflowDefinition

## WorkflowDefinition Sandbox

- Semantic DAG only
- Steps reference TemplateApplicationRequests
- Must pass WorkflowDryRunPlanner
- Does not execute

## Binding Expression Safety

- Binding expressions are inert strings
- No SpEL execution
- No JavaScript execution
- No Python execution
- No executable logic
- Interpreted only by platform-owned compilers

## Security Checklist

- [ ] No executable code in package
- [ ] No network access requested
- [ ] No filesystem access requested
- [ ] No process execution requested
- [ ] No environment variable access
- [ ] No secret access
- [ ] No provider selection
- [ ] No storage internals
- [ ] No signed URLs
- [ ] No local paths
- [ ] All exports validated
- [ ] WorkflowDefinitions pass dry-run
- [ ] Trust level is declarative

## Future WASM / Trusted Code Sandbox

Future work for TRUSTED_SIGNED_CODE packages:
- WASM runtime sandbox
- Process isolation
- Network sandboxing
- Filesystem sandboxing
- Resource limits
- Requires separate ADR
