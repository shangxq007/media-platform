# Control-Plane Governance Contract

**Contract ID:** control-plane-governance
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** PARTIALLY_ALIGNED

## CP-001: User Approval Authority
User approval **MUST** come from explicit user message with full hashes. Memory, Kanban, Agent reports, and automation **MUST NOT** substitute for user approval.

## CP-002: Skill Approval by Full Hash
Skill baseline approval **MUST** use full SKILL.md SHA-256 and canonical tree SHA-256. Short hashes **MUST NOT** be used as sole identifiers.

## CP-003: Root-Owned Backing Store
Approved Skill content **MUST** reside in root-owned backing store (`/var/lib/hermes/approved-skills/`).

## CP-004: Read-Only Live Mount
Live Skill directories **MUST** be mounted read-only. UID 1000 **MUST NOT** be able to write to live Skill paths.

## CP-005: Gateway Role
Gateway **MUST** verify mounts before starting (ExecStartPre). Gateway **MUST** fail closed if mounts are absent.

## CP-006: Memory Role
Memory **MUST** be CONTEXT_AND_AUDIT_ONLY. Memory **MUST NOT** be used as approval authority or architecture authority.

## CP-007: Kanban Acceptance Boundary
Kanban done **MUST NOT** be interpreted as accepted. Only explicit user approval determines acceptance.

## CP-008: Evidence Boundary
.agent-tasks, detached receipts, and forensics **MUST** be classified as EVIDENCE_ONLY. They **MUST NOT** define architecture contracts.

## Known Hardening Gaps
1. Root receipt lifecycle gap
2. umount failure may be masked
3. Same-UID alternate gateway possibility
4. Host reboot not yet verified
5. Native delegate tool restriction unavailable
6. Historical post-task Skill mutation

## Change Authority
- CONTROL_PLANE_ROOT_CHANGE
