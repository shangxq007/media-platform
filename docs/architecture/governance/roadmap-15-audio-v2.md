---
type: architecture-governance-record
milestone: 15
name: AUDIO_V2
status: CLOSED
date: 2026-08-15
authority: AUDIO_V2_BOUNDED_ARCHITECTURE_CONTRACT_V1 (FROZEN, A1-A16)
---

# Roadmap #15 — Audio V2

## Base
- ROADMAP_15_BASE_SHA = e268585ffd550fa816e229c717abab71d3c48122
- ROADMAP_15_BASE_TREE = 1c3016e8e3a57f35393cdbae7a8e08baa292d55f

## Implementation
- IMPLEMENTATION_SHA = (committed on agent/audio-v2; see git log)
- IMPLEMENTATION_TREE = (see git log)

## Audio authority model
- Source audio truth: media-module MediaStream(AUDIO) + SourceAudioDescription (unchanged, A1)
- Audio mix authority: NEW audio-module canonical domain (A2): AudioGain (linear, >=0,
  default 1.0, finite; A4), AudioMute (independent boolean; A5), StereoBalance ([-1,1];
  A6), AudioMix + AudioMasterBus (mix root, not provider config; A8), AudioRoute typed
  routing (clip -> mix -> master; A7), AudioDspNode bounded WHAT (GAIN/EQ/COMPRESSOR/
  LIMITER; A9), AudioDspParam (finite). No Spring beans (pure domain), render -> audio
  sole consumer direction (no cycle).
- Timeline integration (A3/A13): TimelineDocument carries typed AudioMix; audio semantic
  edits alter Timeline revision content hash; no Audio state copy; no second revision DAG.
- Automation (A10): Timeline generic keyframe model remains sole automation authority.
- Loudness (A11): measurement/policy/processing separation preserved; no single DTO.
- Channel semantics (A12): source layout = media truth; mix channels = Audio V2.
- Diff/merge (A14): typed AUDIO_MIX_CHANGED change; Timeline revision graph remains sole
  DAG; disjoint audio edits merge, divergent same-clip edits conflict (existing 3-way).
- Provider (A15): AudioMixFfmpegAdapter (canonical -> volume=/pan=/amix, one-way);
  FFmpeg stays EXECUTION_ONLY; provider syntax never canonical.
- Legacy (A16): TimelineAudioSpec.volume/normalize RETIRED (dead fields, no consumers).

## Persistence
- No new schema: AudioMix serializes inside TimelineDocument payload (deterministic
  jackson SORT path via TimelineContentDigester). No Flyway/jOOQ change.

## Tests / gates
- audio-module: 14 tests (gain/mute/balance/routing/DSP invariants)
- AudioMixFfmpegAdapterTest (Harness-authored): provider translation tests
- TimelineV2AudioRevisionTest: hash semantics + typed diff (8 tests)
- drift: 46/46 (4 new T15 gates: legacy retired / no FFmpeg in audio domain /
  no source metadata copy / AudioMix in canonical content)
- Modulith: render -> audio allowedDependencies registered
- Full suite / bootJar / pfirr1: see FCV evidence

## Scope
#16/#17/#18/#19/#20/#22/#24 = 0 semantic delta.

## Deferred
sends/returns, multichannel positioning, loudness target details, broader DSP catalog;
artifact-pin existence validation (unchanged, Checkpoint A after #19).

## Harness POC
DeepSeek Harness 0.1.0-rc.6 executed AUDIO_V2_I4_FFMPEG_ADAPTER (first real bounded
coding task): first-pass accepted, scope clean, quality good. Evidence summary in
DEEPSEEK_HARNESS_POC_EVIDENCE (FCV report).

## Blockers
0. NEXT: Roadmap #16.
