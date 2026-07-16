# Skill Change Disposition

## Kanban Skill

```
Starting hash: 54827b331dc99fcaa3aa0dae16c1256a41e0c4547b7e8e1fc1b9c0178db10853
Observed changed hash: 7705362f4daa052cbf8c508be1f4d6cd9a9df289831832ca6c4c14c75dc1bc1c
Change timestamp: 2026-07-16 10:44:34
Change source: UNPROVEN_EXTERNAL_CHANGE
Authorization: NOT PROVEN
```

### Investigation Summary

- File Birth = Modify timestamp (created, not modified)
- Curator patches were refused by safety check
- No curator run on Jul 16
- No explicit tool call found at 10:44:34
- Content did not contain unauthorized additions but had 101 extra lines

### Restoration

```
Method: skill_manage(action='edit') with exact 2C starting content
Final hash: 54827b331dc99fcaa3aa0dae16c1256a41e0c4547b7e8e1fc1b9c0178db10853
Result: RESTORED_TO_2C_STARTING_HASH
```

## Java-Test-Repair Skill

```
Starting hash: 225b6efb871db0d068165c6edfa127a88f51dfbf2019fc178e194c758e120618
Final hash: 225b6efb871db0d068165c6edfa127a88f51dfbf2019fc178e194c758e120618
Changed: NO
```
