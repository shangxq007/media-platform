# Control-Plane Phase Handoff

## Task: ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A

## .6A → .7 Handoff

### What .6A Delivers

1. Root-owned receipt store with atomic non-overwriting writes
2. Receipt writer daemon with peer credential validation
3. Systemd sandbox for receipt writer
4. Service credential protection
5. Protected path enforcement (skill mounts + receipt store)
6. 14 governance artifacts
7. 7 control-plane scripts
8. Persistent control-plane review receipt
9. Persistent control-plane verification receipt
10. Debt closure for ROOT-RECEIPT, UMOUNT, SAME-UID, DELEGATE-TOOL

### What .7 Must Do

1. Program-wide final acceptance
2. Full governance chain reconciliation (.1 through .6A)
3. Optional authorized host reboot verification (if user grants permission)
4. Remaining historical receipt review
5. Final debt closure decision
6. V5 unblock decision (only if independently justified)

### Open Items for .7

- HOST-REBOOT-DEBT-001: full reboot verification pending user authorization
- V5: remains QUARANTINED_BLOCKED until .7 independently decides
- Frontend: remains PAUSED until backend contracts stabilize

### Evidence Chain

All .6A evidence is in the repository governance commit and the host installation.
Persistent receipts at /var/lib/hermes/receipts/ provide immutable verification evidence.
