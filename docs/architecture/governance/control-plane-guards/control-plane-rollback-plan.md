# Control-Plane Rollback Plan

## Task: ARCH-DOC-GOV-CONTROL-PLANE-GUARDS.6A

## Rollback Scope

This rollback covers ONLY the control-plane components added by .6A:
- hermes-receipt-writer.service
- Receipt writer scripts
- Socket directory
- Service credential

It does NOT touch:
- hermes-gateway.service (existing, from 2C-L1)
- hermes-approved-skills.service (existing, from 2C-L1)
- Skill mounts (existing)
- Persistent receipts (preserved by default)

## Rollback Command

```bash
sudo bash scripts/control-plane/rollback-hermes-control-plane.sh \
    /var/backups/hermes-control-plane/<timestamp>
```

## Rollback Steps

1. Stop receipt writer service
2. Disable receipt writer service
3. Remove hermes-receipt-writer.service unit
4. Remove receipt writer scripts from /usr/local/bin/
5. Remove socket directory
6. Restore backed-up systemd units
7. daemon-reload

## What Is Preserved

- Persistent receipts at /var/lib/hermes/receipts/
- Service credential at /usr/local/libexec/hermes/receipt-writer-credential
- Gateway and approved-skills services unchanged
- Skill mounts unchanged

## Post-Rollback Verification

```bash
# Verify receipt writer stopped
systemctl is-active hermes-receipt-writer.service  # should fail

# Verify gateway still running
systemctl is-active hermes-gateway.service  # should be active

# Verify skill mounts intact
mount | grep hermes  # should show both mounts
```

## Re-Installation After Rollback

```bash
sudo bash scripts/control-plane/install-hermes-control-plane.sh
```
