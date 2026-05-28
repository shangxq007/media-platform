# Sandbox Runtime Module — Security Configuration

## Overview

The sandbox runtime module provides code execution capabilities for user-submitted scripts.
**Production environments must NOT enable in-process eval.**

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        platform-app                              │
│                                                                  │
│  SandboxController ──► SandboxRuntimeService                     │
│                              │                                   │
│              ┌───────────────┼───────────────┐                   │
│              │               │               │                   │
│         execution-mode  execution-mode   execution-mode          │
│         DISABLED        EXTERNAL         IN_PROCESS              │
│              │               │               │                   │
│              ▼               ▼               ▼                   │
│         reject        SandboxWorkerPort   ScriptEngine.eval     │
│                        (HTTP client)      (dev/test only)        │
│                              │                                   │
│                              ▼                                   │
│                   ┌─────────────────────┐                        │
│                   │  External Worker    │                        │
│                   │  (isolated container)│                        │
│                   └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `sandbox.enabled` | `false` | Master switch. When false, all execute requests are rejected. |
| `sandbox.execution-mode` | `disabled` | Execution mode: `disabled`, `external`, or `in-process`. |
| `sandbox.allow-in-process-eval` | `false` | Allow `ScriptEngine.eval()` in the main JVM. **MUST be false in production.** |
| `sandbox.allowed-languages` | `[]` | Comma-separated list of allowed languages. Empty = none allowed. |
| `sandbox.max-execution-seconds` | `5` | Maximum execution time. Clamped to [1, 120]. |
| `sandbox.max-output-bytes` | `1048576` | Maximum output size (1MB). |
| `sandbox.worker.base-url` | `""` | External worker base URL (e.g., `http://sandbox-worker:8091`). |
| `sandbox.worker.connect-timeout-ms` | `1000` | HTTP connect timeout. |
| `sandbox.worker.read-timeout-ms` | `5000` | HTTP read timeout. |

## Execution Modes

| Mode | Description | Production? |
|------|-------------|-------------|
| `disabled` | All code execution is rejected. Safest default. | ✅ Yes |
| `external` | Code is sent to an external sandbox worker via HTTP. Worker handles isolation. | ✅ Yes (with worker) |
| `in-process` | Code runs in the main JVM via `ScriptEngine.eval()`. Blocklist-based protection. | ❌ Never |

## Profile Behavior

| Profile | `enabled` | `execution-mode` | `allowed-languages` |
|---------|-----------|------------------|---------------------|
| **default** | `false` | `disabled` | `[]` |
| **dev** | `true` | `external` | `python` |
| **prod** | `false` | `disabled` | `[]` |

## External Worker API Contract

### Request

```
POST /v1/sandbox/execute
Content-Type: application/json

{
  "language": "python",
  "code": "print('hello')",
  "timeoutMs": 5000,
  "maxOutputBytes": 1048576,
  "metadata": {
    "requestId": "req-123",
    "traceId": "trace-456",
    "tenantId": "tenant-a"
  }
}
```

### Response

```json
{
  "status": "SUCCESS",
  "stdout": "hello\n",
  "stderr": "",
  "exitCode": 0,
  "durationMs": 123,
  "truncated": false,
  "errorCode": null,
  "message": null,
  "workerId": "sandbox-worker-1",
  "runtime": "python:3.12"
}
```

### Error Mapping

| HTTP Status | Result Status | Meaning |
|-------------|---------------|---------|
| 2xx | SUCCESS | Execution succeeded |
| 400, 422 | DENIED | Worker rejected request |
| 403 | DENIED | Permission denied |
| 408, 504 | TIMEOUT | Execution timed out |
| 413 | FAILED | Output/request too large |
| 500 | ERROR | Internal worker error |
| Connection refused | ERROR | Worker unavailable |
| Read timeout | TIMEOUT | Worker did not respond in time |

## Why Production Must Not Use In-Process Eval

1. **Blocklist bypass**: `DefaultSandboxSecurityPolicy` uses string matching, which can be bypassed with encoding, obfuscation, or dynamic class loading.
2. **Same-JVM execution**: `ScriptEngine.eval()` runs in the same JVM as the main application. A successful exploit has full access to the application, database credentials, and network.
3. **No resource isolation**: Timeout only limits execution time, not CPU, memory, file system, or network access.
4. **Audit risk**: Dynamic code execution is flagged in security audits.

## How to Enable in Dev/Test

```bash
# Via environment variables
SANDBOX_ENABLED=true
SANDBOX_EXECUTION_MODE=external
SANDBOX_WORKER_BASE_URL=http://sandbox-worker:8091

# Via command line
--sandbox.enabled=true --sandbox.execution-mode=external --sandbox.worker.base-url=http://sandbox-worker:8091
```

## Audit Logging

All execution attempts are logged to `SANDBOX_AUDIT` logger:

```
event=sandbox_execute result=DENIED reason=module_disabled language=javascript
event=sandbox_execute result=ATTEMPT mode=EXTERNAL language=python codeHash=a1b2c3d4e5f6a7b8 codeLength=42 timeoutMs=5000
event=sandbox_execute result=SUCCESS mode=EXTERNAL language=python codeHash=a1b2c3d4e5f6a7b8
event=sandbox_execute result=FAILED mode=EXTERNAL language=python codeHash=a1b2c3d4e5f6a7b8 error=...
event=sandbox_execute result=TIMEOUT mode=EXTERNAL language=python codeHash=a1b2c3d4e5f6a7b8 timeoutMs=5000
```

**Never logs actual code.** Only logs `codeHash` (SHA-256 truncated to 16 hex chars) and `codeLength`.

## Category Constraints

The `audit_records.category` column has:
- **NOT NULL** constraint — every record must have a category
- **CHECK** constraint — only valid `AuditCategory` enum values are allowed
- **UNKNOWN** category — used for historical records that cannot be reliably classified

## Security Test Checklist

- [ ] `sandbox.enabled=false` rejects all execute requests
- [ ] `sandbox.execution-mode=disabled` rejects all execution
- [ ] `sandbox.execution-mode=in-process` + `allow-in-process-eval=false` rejects eval
- [ ] `sandbox.execution-mode=external` calls worker port
- [ ] External mode does NOT call ScriptEngine.eval()
- [ ] Worker unavailable returns clear error
- [ ] Worker timeout returns TIMEOUT status
- [ ] Worker error returns ERROR status
- [ ] Audit log contains codeHash but not actual code
- [ ] Audit log does not contain Authorization/token/secret headers
- [ ] Category CHECK constraint matches AuditCategory enum
- [ ] Category NOT NULL constraint prevents null inserts

## Kubernetes NetworkPolicy

### Overview

The sandbox-worker has a `NetworkPolicy` that blocks all outbound network traffic and restricts inbound access to the platform API only. User-submitted code cannot access any network resources.

### Policy: sandbox-worker-network-policy

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: sandbox-worker-network-policy
  namespace: media-platform
spec:
  podSelector:
    matchLabels:
      app: sandbox-worker
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: media-platform
      ports:
        - protocol: TCP
          port: 8091
  egress: []
```

### Inbound Rules

| Source | Port | Allowed |
|--------|------|---------|
| `app: media-platform` pods | TCP 8091 | ✅ Yes |
| Any other source | Any | ❌ No |

### Outbound Rules

| Destination | Allowed |
|-------------|---------|
| Any | ❌ No (deny all) |

### DNS Access

By default, **DNS outbound is blocked**. The sandbox-worker does not need DNS because user code should not make network requests. If needed later, add explicit egress rule for kube-dns.

### CNI Plugin Requirement

NetworkPolicy requires a CNI plugin that supports the `NetworkPolicy` API (e.g., Calico, Cilium, Weave Net, Antrea). Verify with:

```bash
kubectl get networkpolicy -n media-platform
kubectl describe networkpolicy sandbox-worker-network-policy -n media-platform
```

### Runtime Verification

```bash
# From platform-api pod, access sandbox-worker (should succeed)
kubectl exec deploy/platform-api -n media-platform -- \
  sh -c 'wget -qO- http://sandbox-worker:8091/healthz'

# From unauthorized pod, access sandbox-worker (should fail)
kubectl run net-test --rm -it --image=busybox:1.36 --restart=Never -n media-platform -- \
  sh -c 'wget -T 3 -qO- http://sandbox-worker:8091/healthz || echo blocked'

# From sandbox-worker, access external network (should fail)
kubectl exec deploy/sandbox-worker -n media-platform -- \
  python3 -c "import urllib.request; urllib.request.urlopen('https://example.com', timeout=3)" || echo blocked
```
