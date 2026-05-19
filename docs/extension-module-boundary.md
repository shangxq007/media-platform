# Extension Module Boundary

> **Last updated**: 2026-05-12
> **Status**: Apache Commons Exec still present, JavaCV migration partial
> **Security Review**: Required

## Current State Analysis

### ❌ JavaCV Migration NOT Complete

Despite documentation suggesting Apache Commons Exec was removed, **it remains fully present** in the extension module:

```kotlin
// build.gradle.kts
implementation("org.apache.commons:commons-exec:1.6.0")  // Still present
```

**Three execution hierarchies exist:**
1. `DefaultProcessToolRunner` (Commons Exec + registry validation)
2. `CommonsExecToolRunner` (Commons Exec, no registry)
3. `CommonsExecProcessExecutor` (Commons Exec, output discarded)

## Module Responsibilities

### ✅ Current Responsibilities

**1. Process Execution**
- External command execution via Apache Commons Exec
- Timeout enforcement
- Output capture with size limits
- Working directory management

**2. Tool Registry**
- Executable allowlist management
- Tool definition catalog
- Environment validation
- Capability discovery

**3. Configuration-Driven CLI**
- `app.cli-tools.executables` binding
- `app.cli-tools.tools` recipe management
- Template-based argument construction

**4. Security Enforcement**
- Path traversal protection
- Executable whitelisting
- Null byte injection prevention
- Output size limiting

### ❌ Overlapping Responsibilities

**With render-module:**
- JavaCV handles video processing internally
- Extension module still available for other CLI tools
- Duplicate capability detection

**With sandbox-runtime-module:**
- Both provide execution isolation
- Different security models
- Potential for confusion

## Migration Status

### FFmpeg/FFprobe CLI (Still Active)

**Configuration Example:**
```yaml
app:
  cli-tools:
    executables:
      ffmpeg: /usr/bin/ffmpeg
      ffprobe: /usr/bin/ffprobe
    tools:
      probe:
        executableKey: ffprobe
        args: ["-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", "{input}"]
```

**Usage via Extension API:**
```http
POST /api/v1/extensions/cli-tools/probe/run
{
  "params": {
    "input": "storage://video.mp4"
  }
}
```

### ⚠️ Mixed Architecture

**Video Processing Flow:**
1. JavaCVRenderProvider (direct JNI calls) → Primary video rendering
2. FFmpeg CLI (Commons Exec) → Optional probing/processing
3. Both paths can be active simultaneously

## Allowed Operations

### ✅ Permitted via Extension Module

**1. Registered Tools Only**
- Tools defined in `app.cli-tools.tools`
- Executables in `app.cli-tools.executables`
- Template-based arguments only

**2. Safe Operations**
- Media probing (ffprobe)
- Format conversion (trusted inputs)
- Thumbnail extraction
- Metadata inspection

**3. Configuration-Driven**
```java
// Safe: Uses configured recipe
toolRunner.run(ToolRunRequest.of("ffprobe", 
    List.of("-v", "quiet", "{input}"), 5000));
```

### ❌ Forbidden Operations

**1. Direct Process Creation**
```java
// ❌ NOT ALLOWED in business modules
new ProcessBuilder("any-command").start();
Runtime.getRuntime().exec("any-command");
```

**2. Shell Execution**
```java
// ❌ NOT ALLOWED - no shell access
CommandLine.parse("/bin/sh -c 'rm -rf /'");
```

**3. Arbitrary Commands**
```java
// ❌ NOT ALLOWED - not in registry
toolRunner.run("unknown-tool", List.of("args"));
```

**4. Path Traversal**
```java
// ❌ NOT ALLOWED - blocked by registry
new ToolRunRequest("ffmpeg", List.of("-i", "../../etc/passwd"));
```

## Security Restrictions

### ProcessToolRunner Interface

```java
public interface ProcessToolRunner {
    ToolExecutionResult execute(ToolExecutionRequest request);
    ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy);
}
```

**Security Features:**
- `List<String>` args (no shell concatenation)
- Executable allowlist enforcement
- Timeout support (default 60s)
- Output size limits (4MB)
- Working directory validation

### ToolRegistry Validation

```java
@Component
public class ToolRegistry {
    public boolean isAllowedExecutable(String executable) {
        // Rejects: relative paths, path traversal, unknown executables
        return allowedExecutables.containsKey(executable);
    }
    
    public void registerExecutable(String key, String path) {
        // Requires: absolute path, no traversal, case-sensitive
        validatePath(path);
        allowedExecutables.put(key, path);
    }
}
```

### CliTemplateResolver Protection

```java
@Component
public class CliTemplateResolver {
    public List<String> resolve(List<String> templates, Map<String, String> params) {
        // Validates: no empty placeholders, no missing keys, no null bytes
        validateParams(params);
        return templates.stream()
            .map(t -> substitute(t, params))
            .collect(Collectors.toList());
    }
}
```

## Sandbox Runtime Relationship

### Current Separation

**extension-module:**
- Process-based isolation
- Linux namespaces (planned)
- Resource limits (CPU, memory, I/O)
- Network restrictions

**sandbox-runtime-module:**
- Wasm-based isolation
- Language runtime sandboxing
- Script execution only
- No native process access

### Future Integration

```java
// Unified execution facade
public interface SecureExecutor {
    Result execute(ExecutionRequest request);
    
    enum Type {
        PROCESS,    // extension-module
        WASM,       // sandbox-runtime-module
        JNI         // JavaCV direct
    }
}
```

## Compatibility Migration Module

### Script Migration Support

**Purpose:** Migrate legacy scripts to new security model

**Migration Steps:**
1. Identify all `ProcessBuilder` calls
2. Move to extension-module APIs
3. Register tools in configuration
4. Validate security constraints

**Example Migration:**
```java
// BEFORE: Direct execution
Process p = new ProcessBuilder("ffmpeg", "-i", input).start();

// AFTER: Extension module
ToolExecutionResult result = toolRunner.execute(
    ToolExecutionRequest.of("ffmpeg-probe", Map.of("input", input))
);
```

## Configuration Reference

### Application.yml Structure

```yaml
app:
  cli-tools:
    executables:
      ffmpeg: /usr/bin/ffmpeg
      ffprobe: /usr/bin/ffprobe
      convert: /usr/bin/convert
    tools:
      probe:
        executableKey: ffprobe
        args: ["-v", "quiet", "-print_format", "json", "{input}"]
        timeoutMillis: 30000
      thumbnail:
        executableKey: ffmpeg
        args: ["-i", "{input}", "-ss", "00:00:01", "-vframes", "1", "{output}"]
        timeoutMillis: 10000
  extensions:
    plugins-dir: ./plugins
    enabled: true
```

### ToolSandboxPolicy

```java
public record ToolSandboxPolicy(
    Duration timeout,
    String workingDirectory,
    long maxOutputBytes,
    Set<String> allowedOutputPaths,
    boolean networkAccess
) {
    public static ToolSandboxPolicy defaults() {
        return new ToolSandboxPolicy(
            Duration.ofSeconds(60),
            null,
            4 * 1024 * 1024,  // 4MB
            Set.of(),
            false  // No network
        );
    }
}
```

## API Endpoints

### Public API

```http
# List extensions
GET /api/v1/extensions

# Execute registered tool
POST /api/v1/extensions/cli-tools/{toolKey}/run
{
  "params": {
    "input": "storage://file.mp4",
    "output": "storage://thumb.jpg"
  }
}

# List available tools
GET /api/v1/extensions/cli-tools
```

### Internal API (Use with Caution)

```http
# Direct tool execution (requires authentication)
POST /api/v1/extensions/tool-run
{
  "executable": "/usr/bin/ffprobe",
  "args": ["-v", "quiet", "{input}"],
  "timeout": 30000
}
```

## Security Audit Checklist

### Before Production Deployment

- [ ] Review all registered executables
- [ ] Validate allowlist paths are absolute
- [ ] Test timeout enforcement
- [ ] Verify output size limits
- [ ] Check working directory isolation
- [ ] Audit template parameter validation
- [ ] Confirm no `ProcessBuilder` in business code
- [ ] Review network access policies
- [ ] Validate resource limits (CPU, memory)

### Regular Security Tasks

- [ ] Review tool execution logs
- [ ] Monitor for timeout patterns
- [ ] Check for failed validation attempts
- [ ] Update executable allowlist as needed
- [ ] Audit plugin directory contents

## Future Direction

### JavaCV Migration Path

**Goal:** Complete removal of CLI-based video processing

**Steps:**
1. Move all video processing to JavaCV
2. Deprecate FFmpeg CLI tools
3. Remove Commons Exec dependency
4. Keep extension module for non-video tools

**Timeline:**
- Q3 2026: Deprecate FFmpeg CLI in favor of JavaCV
- Q4 2026: Remove video-related CLI tools
- Q1 2027: Complete Commons Exec removal (if no other tools need it)

### Enhanced Security Model

```java
// Future: Capability-based access control
@PreAuthorize("hasCapability('tool.ffmpeg.execute')")
public ToolExecutionResult executeFfmpeg(...) {
    // Fine-grained permission control
}
```

---

*This document reflects the current state as of 2026-05-12. The JavaCV migration is incomplete - Apache Commons Exec remains in use for CLI-based tools.*