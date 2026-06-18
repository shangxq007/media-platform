---
status: blueprint
last_verified: 2026-06-18
scope: future
truth_level: target
owner: platform
---

# Module Blueprint: Automation & Plugin Platform

> **⚠️ BLUEPRINT ONLY** - This module is not implemented. It describes the target architecture for future development.

## 1. Purpose

The Automation & Plugin Platform enables extensibility through a plugin system, workflow automation, and marketplace integration.

## 2. Responsibilities

- Plugin lifecycle management (install, update, uninstall)
- Plugin sandboxing and security
- Workflow automation and orchestration
- Marketplace integration
- Plugin API gateway

## 3. Non-Responsibilities

- Core platform functionality (provided by other modules)
- Plugin implementation (provided by plugin developers)
- Infrastructure management

## 4. Public Ports / APIs

### Plugin API
- Plugin registration and discovery
- Plugin configuration management
- Plugin execution sandbox

### Workflow API
- Workflow definition and execution
- Trigger management
- Task orchestration

### Marketplace API
- Plugin publishing and distribution
- Plugin reviews and ratings
- Revenue sharing

## 5. Domain Model

### Plugin
- id, name, version
- author, description
- capabilities, permissions
- status, trust_level

### Workflow
- id, name, trigger_type
- tasks, conditions
- schedule, status

### MarketplaceListing
- plugin_id, category
- pricing, ratings
- download_count

## 6. Events Published

- `PluginInstalled` - When plugin is installed
- `PluginActivated` - When plugin is activated
- `WorkflowTriggered` - When workflow executes
- `PluginError` - When plugin fails

## 7. Events Consumed

- `UserAction` - For trigger-based workflows
- `SystemEvent` - For system-triggered automation

## 8. Dependencies Allowed

- `shared-kernel` - For common types
- `identity-access-module` - For plugin authentication

## 9. Dependencies Forbidden

- Direct database access from plugins
- Direct system modification (sandboxed)
- Direct network access (controlled)

## 10. Extension Points

- `PluginProvider` interface - For plugin types
- `TriggerProvider` interface - For trigger types
- `TaskProvider` interface - For workflow tasks

## 11. Security / Tenant Rules

- Plugins run in sandboxed environment
- Permission-based access control
- Resource limits per plugin
- Audit logging for all plugin actions

## 12. Persistence Ownership

- `plugin_definition` table
- `plugin_instance` table
- `workflow_definition` table
- `workflow_execution` table

## 13. Observability

- Metrics: plugin count, execution time, error rate
- Traces: plugin execution, workflow steps
- Logs: plugin lifecycle events

## 14. Current Status

**Status: Not Implemented**

This module is entirely blueprint/roadmap. No runtime implementation exists.

### Planned Features
- PF4J-based plugin system
- Workflow automation engine
- Plugin marketplace
- Plugin sandboxing

## 15. Gap to Blueprint

| Blueprint Feature | Current Status | Gap |
|-------------------|----------------|-----|
| Plugin system | Not implemented | Critical |
| Workflow engine | Not implemented | Critical |
| Marketplace | Not implemented | Critical |
| Plugin sandboxing | Not implemented | Critical |
| Plugin API | Not implemented | Critical |

---

## 16. Relationship to Capability Opening Blueprint

See [Capability Opening Blueprint](capability-opening-blueprint.md) for the phased approach to system capability opening.

### Role in Capability Opening Model

| Concept | Role | Description |
|---------|------|-------------|
| SystemAction | Callable unit | The atomic work unit that automation flows execute |
| ExtensionPoint | Stable contract | The SPI that defines how providers extend capabilities |
| ExtensionProvider | Implementation | The provider that implements an ExtensionPoint |
| AutomationFlow | Orchestration | Config-only workflow that chains SystemActions |
| AutomationTrigger | Entry point | Event/schedule/manual trigger for flows |
| ConnectorProvider | Marketplace item | Reviewed connector installable by tenants |
| PluginManifest | Plugin declaration | Declares plugin capabilities and permissions |

### Key Clarifications

1. **Automation is orchestration layer** - Automation flows execute SystemActions, they don't implement them
2. **SystemAction is the callable unit** - Actions are defined elsewhere, automation calls them
3. **ExtensionPoint is the stable contract** - Providers implement ExtensionPoints
4. **Marketplace is future, not current** - Connector marketplace (Level 3) and plugin marketplace (Level 4) are deferred
5. **Early phases are internal and config-only** - Levels 0-2 don't require marketplace infrastructure

### Capability Opening Levels

| Level | Description | Automation Role |
|-------|-------------|----------------|
| Level 0 | Internal System Actions | Actions exist as services, automation can call them |
| Level 1 | Internal Automation Flows | Automation engine executes config-only flows |
| Level 2 | ExtensionPoint / Provider SPI | Automation can invoke providers via ExtensionPoints |
| Level 3 | Connector Marketplace | Automation can use marketplace connectors |
| Level 4 | Reviewed Plugin Marketplace | Automation can orchestrate plugin execution |
| Level 5 | Sandbox / Container Runtime | Automation can execute sandboxed code |

### Non-Goals

- ❌ Automation should not implement arbitrary code execution in early phases
- ❌ Automation should not bypass tenant permissions
- ❌ Automation should not expose production secrets
- ❌ Automation should not allow ungated marketplace installation
