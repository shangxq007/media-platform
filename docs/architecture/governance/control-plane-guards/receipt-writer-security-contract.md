# Receipt Writer Security Contract

## Service Overview

The receipt writer is a Python daemon managed by systemd. It accepts
structured JSON submissions over a Unix domain socket and persists them
to the root receipt store at `/var/lib/hermes/receipts`.

## Input Channel

| Property | Value |
|----------|-------|
| Protocol | Unix domain socket |
| Format | One JSON object per connection |
| Max payload | 64 KiB |

## Schema Validation

Every incoming receipt is validated against the canonical schema before
any disk write occurs.

### Decision Enum

Allowed decision values:

- `PASS`
- `FAIL`
- `BLOCKED`

**Explicitly rejected:** `CONDITIONAL_PASS` — this value is not in the
enum and must never appear in persisted receipts.

### Required Fields

Every receipt must include at minimum:

- `subject` — identifier of what is being receipted
- `decision` — one of the approved enum values
- `task` — normalized task name (used in filename)
- `timestamp` — ISO 8601 UTC

## Ordering Constraint

Verification receipts must not be written unless a corresponding review
receipt already exists in `review/`. The writer enforces
review-before-verification ordering at the filesystem level by checking
for the review file before accepting a verification submission.

## Write Path Security

### Atomic Writes

1. Content is serialized to a temporary file within the target directory.
2. The temp file is `fsync(2)`'d.
3. The temp file is `rename(2)`'d to the final name with `O_EXCL` semantics.
4. The parent directory is `fsync(2)`'d.

No receipt content is ever visible in its final path until fully written.

### No Shell Invocation

Receipt fields are **never** interpolated into shell commands, shell
scripts, or `subprocess` calls with `shell=True`. All subprocess calls
use argument lists with no field-derived content, or avoid subprocess
entirely.

### No Symlink Following

The writer resolves the real path of the target file and verifies it
falls within the receipt root (`/var/lib/hermes/receipts`). Symlinks
pointing outside the root cause immediate rejection of the submission.

## Peer Credential Validation

The writer obtains the peer's UID/GID/PID via `SO_PEERCRED` on the
Unix socket after `accept()`.

| Check | Enforcement |
|-------|-------------|
| UID whitelist | Only permitted UIDs may submit receipts |
| GID verification | Peer must belong to the expected group |
| PID logging | Peer PID is recorded in the receipt audit log |

## Service Credential Management

| Method | Detail |
|--------|--------|
| `systemd LoadCredential` | Service credentials are loaded via systemd's credential mechanism, not environment variables or hard-coded paths |
| Filesystem isolation | The service runs with `ProtectSystem=strict`; credentials are mounted read-only |
| Rotation | Credentials are rotated by updating the systemd unit and restarting the service |

## Error Handling

- Invalid JSON: connection closed, logged, no file written.
- Schema violation: connection closed, logged, receipt recorded in `rejected/`.
- Duplicate subject: rejected with error, no overwrite.
- Socket error: logged, connection dropped.
