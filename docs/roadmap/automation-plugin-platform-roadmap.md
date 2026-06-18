---
status: roadmap
last_verified: 2026-06-18
scope: future
truth_level: target
owner: platform
---

# Automation & Plugin Platform Roadmap

> **Last verified:** 2026-06-18
> **Status:** Not implemented — this is a future vision document

---

## Current State

### What Exists Today

| Component | Status | Notes |
|-----------|--------|-------|
| `extension-module` | ✅ Implemented | CLI tool execution (Commons Exec) + ToolRegistry |
| `sandbox-runtime-module` | ⚠️ Stub | Wasm/Container sandbox (not yet implemented) |
| Plugin directory config | ✅ Configured | `app.extensions.plugins-dir: ./plugins` |
| CLI tool recipes | ✅ Working | Template-based argument construction |
| `ToolSandboxPolicy` | ✅ Designed | Resource limits, timeouts, output size |
| Process isolation | ⚠️ Partial | Linux namespaces (planned, not implemented) |

### What Does NOT Exist

| Component | Status | Impact |
|-----------|--------|--------|
| Plugin lifecycle management | ❌ Not implemented | No install/uninstall/upgrade flow |
| Plugin marketplace/registry | ❌ Not implemented | No central plugin discovery |
| Plugin versioning | ❌ Not implemented | No compatibility checks |
| Plugin dependency resolution | ❌ Not implemented | No transitive dependency handling |
| Plugin security sandbox (Wasm) | ❌ Not implemented | No untrusted code execution |
| Plugin API contracts | ❌ Not implemented | No formal SPI for plugin authors |
| Plugin configuration UI | ❌ Not implemented | No admin panel for plugin management |

---

## Future Vision

### Phase 1: Extension Points (Q3-Q4 2026)

**Goal:** Establish formal extension points for internal use

**Scope:**
- Define SPI interfaces for key platform capabilities
- Create extension point registry
- Enable internal teams to extend platform via well-defined interfaces

**Extension Points:**
```java
// Render Provider SPI
public interface RenderProvider {
    String name();
    boolean supports(Format format);
    RenderResult render(RenderRequest request);
}

// Storage Provider SPI
public interface StorageProvider {
    String name();
    PutResult put(PutObjectCommand command);
    GetResult get(GetObjectCommand command);
}

// Notification Channel SPI
public interface NotificationChannel {
    String name();
    void send(Notification notification);
}
```

**Deliverables:**
- [ ] Extension point documentation
- [ ] SPI interfaces in `shared-kernel`
- [ ] Example implementations
- [ ] Unit/integration tests

---

### Phase 2: Plugin Packaging (Q1 2027)

**Goal:** Enable self-contained plugin packages

**Plugin Structure:**
```
plugins/
├── my-plugin/
│   ├── plugin.yml          # Metadata (name, version, author)
│   ├── manifest.json       # Capabilities, dependencies
│   ├── classes/            # Compiled Java/Kotlin classes
│   ├── lib/                # Plugin dependencies
│   └── config/             # Default configuration
```

**Plugin Manifest:**
```yaml
name: my-render-provider
version: 1.0.0
author: team@example.com
description: Custom render provider for internal workflows
platform-version: ">=2.0.0"
capabilities:
  - render-provider
  - storage-provider
dependencies:
  - name: ffmpeg-wrapper
    version: ">=4.0.0"
config:
  my.provider.quality: "high"
  my.provider.threads: 4
```

**Deliverables:**
- [ ] Plugin packaging format specification
- [ ] Plugin loader (classloader isolation)
- [ ] Plugin dependency resolver
- [ ] Plugin configuration binding

---

### Phase 3: Plugin Lifecycle (Q2 2027)

**Goal:** Manage plugin installation, activation, and updates

**Lifecycle States:**
```
DISCOVERED → INSTALLED → RESOLVED → ACTIVE → STOPPED → UNINSTALLED
```

**API:**
```http
# List installed plugins
GET /admin/plugins

# Install plugin from registry
POST /admin/plugins/install
{
  "name": "my-render-provider",
  "version": "1.0.0",
  "source": "https://plugins.example.com"
}

# Activate plugin
POST /admin/plugins/{pluginId}/activate

# Deactivate plugin
POST /admin/plugins/{pluginId}/deactivate

# Uninstall plugin
DELETE /admin/plugins/{pluginId}
```

**Deliverables:**
- [ ] Plugin lifecycle manager
- [ ] Plugin health checks
- [ ] Plugin metrics collection
- [ ] Admin API endpoints
- [ ] Admin UI (basic)

---

### Phase 4: Plugin Marketplace (Q3-Q4 2027)

**Goal:** Centralized plugin discovery and distribution

**Features:**
- Plugin registry (self-hosted or cloud)
- Version management and compatibility checks
- Security scanning and approval workflow
- Usage analytics and ratings

**Registry API:**
```http
# Search plugins
GET /registry/plugins?q=render&category=media

# Get plugin details
GET /registry/plugins/{pluginId}

# Get plugin versions
GET /registry/plugins/{pluginId}/versions

# Download plugin
GET /registry/plugins/{pluginId}/download/{version}
```

**Deliverables:**
- [ ] Plugin registry service
- [ ] Security scanning pipeline
- [ ] Approval workflow
- [ ] Usage analytics dashboard

---

## Dependencies & Prerequisites

### Technical Prerequisites

| Prerequisite | Status | Required For |
|--------------|--------|--------------|
| `extension-module` stabilization | ⚠️ In progress | Phase 1 |
| JavaCV migration complete | ❌ Blocked | Phase 2 (classloader isolation) |
| Wasm runtime integration | ❌ Not started | Phase 3 (sandbox execution) |
| `sandbox-runtime-module` implementation | ⚠️ Stub | Phase 3 (untrusted code) |
| Classloader isolation | ❌ Not started | Phase 2 (plugin packaging) |
| Plugin configuration binding | ❌ Not started | Phase 2 (plugin config) |

### Operational Prerequisites

| Prerequisite | Status | Required For |
|--------------|--------|--------------|
| Plugin security model | ❌ Not designed | Phase 2 (packaging) |
| Plugin testing framework | ❌ Not designed | Phase 2 (packaging) |
| Plugin documentation generator | ❌ Not designed | Phase 1 (extension points) |
| Plugin rollback mechanism | ❌ Not designed | Phase 3 (lifecycle) |

### Team Prerequisites

| Role | Responsibility | Needed For |
|------|----------------|------------|
| Platform Engineer | SPI design, plugin loader | Phase 1-2 |
| Security Engineer | Plugin sandbox, security model | Phase 2-3 |
| DevOps Engineer | Plugin registry, CI/CD integration | Phase 3-4 |
| Frontend Engineer | Plugin admin UI | Phase 3-4 |

---

## Architecture Decisions

### ADR-001: Plugin Isolation Model

**Decision:** Use Java classloader isolation for plugin packaging

**Rationale:**
- Java classloaders provide strong isolation
- No native dependencies required
- Compatible with existing Spring Boot architecture
- Wasm can be added later for untrusted code

**Consequences:**
- Plugin classes cannot access platform internals directly
- Plugin dependencies must be bundled (no shared classpath)
- Plugin-to-plugin communication via platform APIs only

---

### ADR-002: Plugin Configuration Model

**Decision:** Use Spring Boot `@ConfigurationProperties` with plugin namespace

**Rationale:**
- Consistent with existing platform configuration
- Type-safe binding
- Validation support
- Environment variable override

**Example:**
```java
@ConfigurationProperties(prefix = "plugins.my-render-provider")
public class MyRenderProviderProperties {
    private String quality = "high";
    private int threads = 4;
    // getters/setters
}
```

---

### ADR-003: Plugin Communication Model

**Decision:** Plugins communicate via platform event bus and service interfaces

**Rationale:**
- Loose coupling between plugins
- Platform can mediate and audit plugin interactions
- Consistent with existing outbox event pattern

**Example:**
```java
// Plugin publishes event
eventBus.publish(new PluginEvent("my-plugin", "render-complete", result));

// Another plugin subscribes
@EventListener
public void onRenderComplete(PluginEvent event) {
    // Handle event
}
```

---

## Risk Assessment

### High Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Plugin security vulnerabilities | Platform compromise | Security scanning, sandboxing, approval workflow |
| Plugin compatibility breaks | Platform instability | Version compatibility checks, rollback mechanism |
| Classloader leaks | Memory leaks, restarts | Classloader lifecycle management, monitoring |

### Medium Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Plugin dependency conflicts | Runtime errors | Dependency isolation, conflict detection |
| Plugin performance issues | Platform degradation | Resource limits, monitoring, circuit breakers |
| Plugin configuration errors | Misconfiguration | Validation, defaults, documentation |

### Low Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Plugin marketplace abuse | Spam, low-quality plugins | Approval workflow, ratings, reviews |
| Plugin documentation gaps | Developer friction | Auto-generated docs, examples, tutorials |

---

## Success Metrics

### Phase 1 (Extension Points)

- [ ] 3+ SPI interfaces defined and documented
- [ ] 2+ example implementations created
- [ ] 90%+ test coverage for SPI contracts
- [ ] Internal teams can extend platform via SPIs

### Phase 2 (Plugin Packaging)

- [ ] Plugin packaging format specification complete
- [ ] Plugin loader with classloader isolation working
- [ ] 3+ plugins packaged and tested
- [ ] Plugin configuration binding working

### Phase 3 (Plugin Lifecycle)

- [ ] Plugin lifecycle manager operational
- [ ] Admin API endpoints functional
- [ ] Plugin health checks working
- [ ] Plugin metrics collected and visible

### Phase 4 (Plugin Marketplace)

- [ ] Plugin registry operational
- [ ] Security scanning pipeline working
- [ ] 10+ plugins available in marketplace
- [ ] Usage analytics dashboard functional

---

## Open Questions

1. **Plugin signing:** Should plugins be cryptographically signed?
2. **Plugin permissions:** Should plugins request specific permissions (e.g., storage access, network access)?
3. **Plugin sandbox:** Should untrusted plugins run in Wasm sandbox or classloader isolation?
4. **Plugin registry:** Self-hosted or cloud-based? Public or private?
5. **Plugin monetization:** Should marketplace support paid plugins?

---

## Related Documentation

- [Extension Module Boundary](../extension-module-boundary.md)
- [AI Engine SPI](../ai-engine-spi.md)
- [Module Boundaries](../module-boundaries.md)
- [Layering and Open Source](../layering-and-open-source.md)

---

*This roadmap describes a future vision for automation and plugin platform capabilities. Current implementation status is limited to `extension-module` CLI tool execution and stub `sandbox-runtime-module`. All timelines are targets and subject to change based on priorities and resources.*
