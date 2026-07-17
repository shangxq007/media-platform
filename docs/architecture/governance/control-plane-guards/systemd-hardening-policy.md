# Systemd Hardening Policy

This document records the systemd security directives applied to, rejected
for, or marked not applicable to each Hermes service.

## Receipt Writer Service

### Applied Directives

| Directive | Value | Rationale |
|-----------|-------|-----------|
| `NoNewPrivileges` | `yes` | Prevent privilege escalation via `execve`. Blocks setuid binaries and ambient capabilities. |
| `ProtectSystem` | `strict` | Mount `/` and `/usr` read-only. The receipt store at `/var/lib/hermes/receipts` is added via `ReadWritePaths`. |
| `PrivateTmp` | `yes` | Provide an isolated `/tmp` and `/var/tmp` that are not shared with other processes. |
| `PrivateDevices` | `yes` | Deny access to physical devices under `/dev`. Only pseudo-devices like `/dev/null` remain accessible. |
| `ProtectKernelTunables` | `yes` | Make `/proc/sys` and `/sys` read-only, preventing runtime kernel parameter changes. |
| `ProtectKernelModules` | `yes` | Disable explicit module loading via `init_module` and `finit_module` syscalls. |
| `ProtectControlGroups` | `yes` | Make `/sys/fs/cgroup` read-only, preventing cgroup manipulation. |
| `RestrictNamespaces` | `yes` | Deny creation of any Linux namespace (user, mount, net, pid, cgroup, uts, ipc). |
| `RestrictSUIDSGID` | `yes` | Prevent setting the SUID or SGID bits on files in accessible directories. |
| `LockPersonality` | `yes` | Lock the execution domain to prevent `personality(2)` syscall abuse. |
| `CapabilityBoundingSet` | *(empty)* | Drop all Linux capabilities. The service runs with no elevated privileges. |
| `SystemCallArchitectures` | `native` | Reject syscall invocation from non-native architectures, blocking foreign-arch exploit payloads. |
| `RestrictAddressFamilies` | `AF_UNIX AF_INET` | Allow only Unix domain sockets and IPv4. IPv6, netlink, and packet sockets are denied. |
| `UMask` | `0077` | Restrict default file creation permissions to owner-only read/write. |

### Not Applicable

| Directive | Reason |
|-----------|--------|
| `MemoryDenyWriteExecute` | Python's runtime requires writable and executable memory pages for JIT compilation and dynamic module loading. Applying this directive causes the interpreter to crash with `SIGSEGV`. Not compatible with CPython or PyPy runtimes. |
| `ProtectHome` | While the receipt writer does not directly access `/home/user/.hermes`, the directive is omitted to avoid coupling the hardening profile to deployment-specific home directory requirements. If the writer is isolated into a dedicated service account in a future refactor, `ProtectHome=yes` should be applied and the `ReadWritePaths` adjusted accordingly. |

## Gateway Service

### Applied Directives

| Directive | Value | Rationale |
|-----------|-------|-----------|
| `NoNewPrivileges` | `yes` | Prevent privilege escalation via `execve`. Essential since the gateway processes user-supplied content. |
| `ProtectKernelTunables` | `yes` | Make `/proc/sys` and `/sys` read-only, preventing runtime kernel parameter changes. |
| `ProtectKernelModules` | `yes` | Disable explicit module loading. The gateway has no legitimate reason to load kernel modules. |

### Rejected With Reason

| Directive | Reason |
|-----------|--------|
| `ProtectSystem=strict` | The gateway must write to `~/.hermes/` for session state, memory, plugin state, and cron definitions. `ProtectSystem=strict` mounts the entire filesystem read-only except for explicitly listed `ReadWritePaths`. Listing every writable subdirectory (`memories/`, `sessions/`, `plugins/`, `cron/`, etc.) creates fragile coupling that breaks when new directories are added. Rejected until the gateway is migrated to a dedicated service account with explicit, narrowly-scoped write paths. |
| `ProtectHome` | The gateway's entire operational directory is `~/.hermes/`. Protecting home would deny access to skills, plugins, cron, memories, session data, and all configuration. There is no alternative data location configured and no migration plan exists. Rejected until the data directory is relocated to a service-specific path outside `/home`. |

## Directive Interactions

Directives are not independent. Notable interactions:

- `ProtectSystem=strict` + `ReadWritePaths` — the read-write path list
  must include every directory the service actually writes to. Omitting
  a path causes silent write failures. The receipt writer's paths are
  limited to the receipt store, making this manageable.
- `PrivateTmp` + `RestrictAddressFamilies=AF_UNIX` — if the service
  uses temporary Unix sockets, they must be placed in the private tmp
  or in a `ReadWritePaths` directory, not in the host `/tmp`.
- `CapabilityBoundingSet=` + `NoNewPrivileges` — together these
  eliminate all privilege escalation vectors that don't involve a
  kernel exploit.

## Enforcement and Auditing

### Security Scoring

After each unit file change, run:

```bash
systemd-analyze security hermes-receipt-writer.service
systemd-analyze security hermes-gateway.service
```

The output is recorded and retained as evidence of the hardening baseline.
Any score regression requires justification before deployment.

### Change Process

1. Propose directive change with rationale.
2. Update the systemd unit file.
3. Run `systemd-analyze security` and compare to baseline.
4. Restart the service and verify functionality.
5. Update this document to reflect the change.

### Kernel-Level Enforcement

These directives are enforced by the kernel's cgroup, namespace, seccomp,
and mount-namespace boundaries — not by application code. A compromised
service process cannot bypass them without a kernel exploit.

## Future Hardening Roadmap

| Candidate | Blocker | Priority |
|-----------|---------|----------|
| `ProtectHome=yes` on receipt writer | Migrate to dedicated service account | Medium |
| `ProtectSystem=strict` on gateway | Relocate `~/.hermes` to service-specific path | Low |
| `MemoryDenyWriteExecute` on receipt writer | Requires non-Python runtime (e.g., Rust, Go) | Low |
| `LockPersonality` on gateway | None — can be applied now | High |
| `RestrictSUIDSGID` on gateway | None — can be applied now | High |
| Seccomp profile | Custom `@system-service` allowlist | Medium |
