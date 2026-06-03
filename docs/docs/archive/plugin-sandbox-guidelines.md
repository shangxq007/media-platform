# Plugin Sandbox Guidelines

> **Purpose:** Security guidelines for running extensions in the sandbox.  
> **Last Updated:** 2026-05-14

---

## Sandbox Execution Model

All extensions execute in a sandboxed environment provided by `SandboxExecutionService`. The sandbox enforces:

1. **Time limits** - Extensions must complete within the timeout (default 30s, max 120s)
2. **Output limits** - Output is truncated at 4MB
3. **Thread isolation** - Each extension runs in a dedicated thread from a cached thread pool
4. **No network access** - Extensions cannot make network calls by default
5. **No filesystem access** - Extensions cannot read/write files outside the working directory
6. **Exception isolation** - Extension failures do not crash the host application

## Security Policy

### Low-Risk Extensions (TEMPLATE, VALIDATOR)
- Can execute with default timeout (30s)
- No network access
- Read-only filesystem access
- Output limited to 4MB

### Medium-Risk Extensions (RENDER_SCRIPT, POST_PROCESSOR)
- Can execute with extended timeout (60s)
- No network access
- Read/write access to working directory only
- Output limited to 4MB
- Audit logged

### High-Risk Extensions (PROVIDER, CUSTOM)
- Require explicit approval (REQUIRE_REVIEW)
- Can execute with max timeout (120s)
- Network access must be explicitly enabled
- Filesystem access restricted to designated paths
- Output limited to 4MB
- Full audit trail
- Sentry alert on failure

## Extension Approval Workflow

1. **Submit** - Extension is registered with status PENDING_REVIEW
2. **Scan** - Automated security scan checks for dangerous patterns
3. **Review** - High-risk extensions require manual approval
4. **Activate** - Approved extensions become ACTIVE
5. **Monitor** - All executions are audit-logged and monitored

## Dangerous Patterns (Auto-Blocked)

The following patterns in extension code will trigger BLOCK:

- `Runtime.exec`, `ProcessBuilder` - Process execution
- `java.net.URL`, `HttpURLConnection` - Network access (without explicit permission)
- `java.io.File` outside working directory - Filesystem access
- `System.exit`, `Runtime.halt` - JVM shutdown
- `java.lang.reflect` - Reflection (without permission)
- `native` methods - Native code execution

## Rollback Procedures

### Automatic Rollback
- If an extension fails 3 consecutive times, it is automatically marked INACTIVE
- If an extension causes a Sentry alert, it is quarantined for review

### Manual Rollback
```java
// Unload the extension
registry.unloadExtension("extension-key", "admin");

// Rollback to previous version
registry.rollbackExtension("extension-key", "1.0.0", "admin");
```

## Audit Requirements

All extension operations must include:
- `actorType` - Who performed the operation (user, service, system)
- `actorId` - Identifier of the actor
`action` - REGISTER, UNLOAD, ROLLBACK, EXECUTE
- `resourceType` - extension_type
- `resourceId` - extension key
- `category` - EXTENSION
