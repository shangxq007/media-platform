# Frame-rate representation and Render initiation

`shared.time.FrameRate` remains the single exact frame-rate value: positive rational components, gcd normalization and exact arithmetic. `shared.time.MediaTime` and `shared.digest.CanonicalCommandFingerprint` remain unchanged stable values.

Two representation owners replace the retired shared Jackson codec:

- `timeline.api.serialization.TimelineFrameRateCodec` parses Timeline wire rates, including the canonical clip-rate and project-rate inputs consumed by Render projection adapters.
- `render.domain.interchange.RenderFrameRateCodec` parses Render/interchange rate input used by TimelineScriptParser.

Both retain the accepted exact signed-int32 JSON component bounds and construct the same shared FrameRate value. Present malformed, partial, zero/negative or non-integral rates are rejected before narrowing. A caller must explicitly choose the existing optional missing-rate policy; required fields reject absence. The existing optional default remains30/1. No valid representation or canonical hash format changes, and no version reinterpretation is introduced. Existing integer-fps projections remain explicitly lossy projections after validation, never the canonical value.

`render.ir.RationalTime` owns the exact IR time value; canonical IR serialization remains the same rational string. It is not another FrameRate or MediaTime authority.

`render.api.request.RenderInitiator` is the immutable Render request principal snapshot. Its existing JSON `kind` values PRINCIPAL/SYSTEM and actorId/actorType/tenantId fields are unchanged. It snapshots an explicitly resolved canonical actor, performs no identity lookup or authorization, carries no roles/email/provider/audience data, and never infers SYSTEM from absence. Real cross-module consumers use the published `render :: requests` contract.

The three old shared definitions are deleted, without forwarding types or compatibility aliases. These internal contracts add no HTTP endpoint, frontend feature, Project Open bootstrap, provider execution, or deployment readiness.
