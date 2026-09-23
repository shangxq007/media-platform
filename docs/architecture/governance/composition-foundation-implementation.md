# Composition Foundation Implementation

The public capability availability catalog projects stable capability identity and contract versions, typed input/output references, media and asset support, execution modes, availability summaries, cost/quota estimates, retry/cancellation characteristics, and workflow/application compatibility. It does not contain provider, backend, worker, manifest, or registry topology data.

The internal `ProviderRegistryBoundary` resolves registered capability metadata and maps health to the abstract availability result. Unregistered or unavailable capabilities fail closed during validation.

Template Workflows are declarative graphs of capability steps, typed bindings, assets, parameters, alternatives, required capabilities, execution policy, cost, and reliability. They do not start Temporal workflows, own retries, manage workers, send cancellation signals, construct provider processes, or perform side effects. A later Temporal adapter can consume the validated `ValidationResult` without changing this public contract.

Applications compose capabilities and Template Workflows with display metadata, contracts, assets, entitlements, modes, and draft/published lifecycle. Ownership is tenant/workspace scoped and draft writes use revisions; published values are immutable in the public model.

The local implementation is deterministic and non-executing. PVE, Temporal, Storage, FFmpeg, BMF, external providers, worker topology, production configuration, and deployment validation are NOT_RUN for this batch.
