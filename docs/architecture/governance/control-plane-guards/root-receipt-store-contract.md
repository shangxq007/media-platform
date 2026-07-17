# Root Receipt Store Contract

## Storage Location

| Property | Value |
|----------|-------|
| Root path | `/var/lib/hermes/receipts` |
| Owner | `root:root` |
| Directory mode | `0750` |
| File mode | `0640` |

The receipt store is the single authoritative location for all governance
receipts. No other path is valid. Receipts written outside this tree are
not recognized by any tooling.

## Directory Structure

```
/var/lib/hermes/receipts/
├── review/          # Skill review receipts
├── verification/    # Post-execution verification receipts
├── control-plane/   # Control-plane operation receipts
├── rejected/        # Rejected receipt submissions
└── indexes/         # Rebuildable index files
```

Each subdirectory is created at provisioning time with identical ownership
and mode constraints. No other directories exist under the receipt root.
Symlinks are not permitted within the tree.

## File Naming Convention

Receipts follow a deterministic naming pattern:

```
<normalized-task>--<receipt-type>--<subject-hash>.json
```

| Segment | Description | Example |
|---------|-------------|---------|
| `normalized-task` | Lowercase, hyphenated task identifier | `skill-review` |
| `receipt-type` | Receipt category matching parent directory | `review` |
| `subject-hash` | SHA-256 hex of the subject being receipted | `a1b2c3d4e5f6...` |

Extensions are always `.json`. Filenames contain no path separators,
no shell metacharacters, and no Unicode outside ASCII printable range.

Filename length is bounded by the Linux `NAME_MAX` (255 bytes). The
writer enforces this at validation time.

## Write Guarantees

### No Overwrite

All writes use `O_EXCL` — the file must not already exist. A duplicate
submission is rejected with an error code, not silently overwritten.
This ensures receipts are append-only from the perspective of the store.

### Atomic Rename

Content is written to a temporary file in the same directory as the
target, then `rename(2)` into the final name. Readers never see partial
content because `rename(2)` is atomic on POSIX-compliant filesystems.

### Fsync Discipline

Both the temporary file and the parent directory are `fsync(2)`'d before
the rename. This guarantees durability across power loss or kernel panic.
The write sequence is:

1. `open(tmpfile, O_WRONLY | O_CREAT | O_EXCL)`
2. Write content.
3. `fsync(tmpfile)`.
4. `rename(tmpfile, final_name)`.
5. `fsync(directory_fd)`.

## Index Rebuildability

Files under `indexes/` are derived views. They are always rebuildable
by scanning the immutable receipt files in `review/`, `verification/`,
`control-plane/`, and `rejected/`.

No receipt file is ever modified after creation. Index files are the
only mutable artifacts and carry no authority beyond convenience. An
index that becomes corrupted can be deleted and rebuilt from the
immutable receipt files without data loss.

## Immutability Invariant

Once a receipt file is written, it is never modified. There is no
update, patch, or edit operation. The only mutation permitted on the
receipt store is the addition of new files. Deletion is restricted to
authorized administrative action and is logged independently.
