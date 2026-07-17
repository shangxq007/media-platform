# Service Identity and Gateway Policy

## Current State

| Component | UID | Notes |
|-----------|-----|-------|
| Gateway | 1000 (`user`) | Runs under the primary user account |
| Receipt writer | root | Runs as a system service |

The gateway runs as UID 1000 (`user`) — the same UID that owns all
user data, configuration, and skill files under `/home/user/.hermes`.

## Dedicated Service Account Decision

A dedicated service account for the gateway was **not** created.

### Rationale

Creating a separate UID for the gateway would require migrating
ownership of all files the gateway reads and writes:

- Skill definitions under `~/.hermes/skills/`
- Plugin state under `~/.hermes/plugins/`
- Cron definitions under `~/.hermes/cron/`
- Memory files under `~/.hermes/memories/`
- Session databases under `~/.hermes/`

This migration carries significant risk of permission breakage,
data loss during transition, and operational complexity. The benefit
does not justify the cost at current scale.

## Mitigations (Without Dedicated UID)

### Root-Owned Configuration

- The systemd unit file is owned by root and not writable by UID 1000.
- Credential files referenced by `LoadCredential` are root-owned with
  mode `0400`.
- The receipt store at `/var/lib/hermes/receipts` is root-owned.

### Systemd Unit Protection

- The gateway unit applies available hardening directives (see
  [systemd-hardening-policy.md](systemd-hardening-policy.md)).
- The unit is managed by the system package manager; local edits are
  tracked and flagged on upgrade.

### Credential Protection

- No secrets are stored in environment files readable by UID 1000.
- `LoadCredential` provides credentials to the service without
  exposing them in the filesystem namespace of other user processes.

## Same-UID Bypass Mitigation

Because the gateway and user processes share UID 1000, filesystem-level
access control cannot distinguish between them. The receipt writer
compensates by validating peer credentials:

1. The gateway connects to the receipt writer's Unix socket.
2. The receipt writer calls `SO_PEERCRED` to obtain the peer's UID,
   GID, and PID.
3. Even though the UID matches the user, the writer verifies the
   connection comes from the expected process (gateway) based on
   process metadata.
4. Unauthorized processes sharing UID 1000 are rejected.

This provides a process-level access control layer that filesystem
permissions alone cannot offer.

## Old User Gateway

Any previously-installed gateway service running under the `user`
account (e.g., a user-level systemd unit) is **masked** to prevent
conflict with the current system-level unit.

```
systemctl --user mask hermes-gateway.service
```

The masked unit cannot be started, even accidentally.
