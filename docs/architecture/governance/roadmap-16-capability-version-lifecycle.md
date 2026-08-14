---
type: architecture-governance-record
milestone: 16
name: CAPABILITY_VERSION_LIFECYCLE
status: CLOSED
date: 2026-08-15
authority: CAPABILITY_VERSION_LIFECYCLE_BOUNDED_ARCHITECTURE_CONTRACT_V1 (FROZEN, C1-C20 + R1-R4)
---

# Roadmap #16 — Capability Version / Lifecycle

## Base
- ROADMAP_16_BASE_SHA = 23f1cbabd20484aad5595ec297d242321d1464d3
- ROADMAP_16_BASE_TREE = 94935fc50a10fa6cce7a96007b3ee4b275ed5020

## Implementation
- IMPLEMENTATION_SHA = 0174c61ecedcd2236308bbb41d237222e3f809bb
- IMPLEMENTATION_TREE = 4d854607aa4def3b324e87b216fd3a443ff3ef4d
- IMPLEMENTATION_TREE = (see git log)

## Authority model (R1-R4 refinements applied)
- CapabilityId = typed namespaced identifier (platform reserved media./timeline./audio./
  video./subtitle./render. vs vendor reverse-DNS com./org./net./io./dev.); CapabilityNamespaceValidator
  fails closed on squatting/malformed ids; PluginDescriptorValidator enforces PLG-017.
- ContractVersion / ContractVersionRange = typed major.minor with explicit numeric
  compatibility (major-matched range); "higher plugin version" never implies contract
  compatibility (C2/C12).
- CapabilityRequirement = explicit consumer contract (CapabilityId + range + required/
  optional + alternatives); never references plugin/plan (C4).
- CapabilityImplementation + CapabilityImplementationId = INDEPENDENT identity (not the
  (plugin, capability) tuple); same plugin can provide multiple implementations of the
  same capability; duplicate implementation id fails closed PLG-018 (R2/C5).
- Registry: PluginRegistryPort interface = architecture contract authority;
  PluginRegistryImpl = current implementation; capability-level queries
  (findCapabilityImplementations/findImplementationById) added (R3/C10).
- Multi-axis lifecycle: CapabilityContractLifecycle (ACTIVE/RETIRED) !=
  RegistrationAvailability (DISCOVERED/VALIDATED/AVAILABLE/DEGRADED/UNAVAILABLE) !=
  PluginHealth (plugin axis); no single boolean carries all layers (R4/C13/C14).
- Dependency: CapabilityDependencyValidator — missing required / incompatible contract
  fail closed; optional missing allowed; direct-cycle detection hook (C11).
- Entitlement boundary: zero proOnly/enterpriseOnly/quota fields in capability contract
  (Gate 4, tests) (C17).
- Provider/Worker: never enter capability definition (C7/C8).

## Tests / gates
- CapabilityV16ContractTest: 19 tests (namespace/version/requirement/implementation/
  registry/registration/lifecycle/dependency/entitlement)
- drift: 51/51 (5 new T16 gates)
- full suite / bootJar / pfirr1: see FCV evidence
- Modulith: unchanged (extension-module only, no new module)

## Scope
#17/TemporalMapping/OperationModel/#18/#19/#20/#22/#23/#24/MCP/Embabel/Agents/Marketplace/
RecipeDSL = 0 semantic delta. EffectiveCapabilityView = boundary only.

## Deferred
full EffectiveCapabilityView, billing/entitlement engine, Marketplace, plugin install
transaction, sandbox/trust/signing, conformance certification, semantic extension runtime,
MCP/Embabel/Agent/Skills/Recipe/Template/DSL, Temporal Mapping, Operation Model,
Provider/Worker fabric; artifact-pin existence validation (unchanged, Checkpoint A).

## Harness POC
No forced tasks this round (decision-recovery + bounded implementation executed by
Hermes; harness evidence continues accumulating from #15 sample).

## Blockers
0. NEXT: Roadmap #17.
