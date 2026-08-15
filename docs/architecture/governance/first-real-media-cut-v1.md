---
type: architecture-governance-record
milestone: FRMC
name: FIRST_REAL_MEDIA_CUT_V1
status: CLOSED
date: 2026-08-15
authority: existing canonical stack (Media V2 + Timeline V2 + Audio V2 + Capability + Source Binding + Version Governance)
---

# FIRST_REAL_MEDIA_CUT_V1

## Base
- FIRST_REAL_MEDIA_CUT_BASE_SHA = d77fe84d1b292b4ba75bc710c255b88a077fdb0c
- FIRST_REAL_MEDIA_CUT_IMPLEMENTATION_SHA = a1c3d328e55f97cb9f5e05cb078ac53471ce2407
- FIRST_REAL_MEDIA_CUT_IMPLEMENTATION_TREE = 66088428d025c3fb6484f06231c584f92dee244b
- FIRST_REAL_MEDIA_CUT_PUBLICATION_SHA = (see git log)
  (VCG post-close correction; contains #13-#17 + VERSION_COMPATIBILITY_GOVERNANCE)
- VCG_POST_CLOSE_FINALIZATION = PASS (ReleaseVersion E.R.P verified; /api/vN routes = 0;
  credential residue = 0; OASDIFF_CHECKSUM_PIN = NO — non-blocking hardening observation)

## Real media cut (product-semantic validation)
- INPUT: deterministic MP4 fixture (H.264 1280x720 30fps + AAC 48kHz stereo, 12s,
  4 machine-distinguishable sections red/green/blue/white + 440/660/880/220Hz)
  INPUT_FILE_SHA256 = bc1059dc6029e4c51165b9806ff8b209cfcd774c5d2bcd99ce5d2e1bb7b4abb8
- GOLDEN EDIT: 4 clips, source order A,B,C,D -> OUTPUT order A,C,B,D
  CLIP1 A[0,2.5] normal | CLIP2 C[6,8] gain 0.4 | CLIP3 B[3.5,5.5] mute |
  CLIP4 D[9,11] balance full-left; omitted intervals [2.5,3.5][5.5,6][8,9][11,12]
- TIMELINE: TimelineSourceBinding/MEDIA_STREAM; immutable ArtifactId+ContentDigest pins;
  exact rational MediaTime; Audio V2 gain/mute/balance; deterministic hash
- EXECUTION: existing FFmpeg adapter (adapter = HOW, never canonical WHAT); FFmpeg 7.0.2
- OUTPUT: MP4 H.264 1280x720 30/1 + AAC 48kHz 2ch, 8.500s
  OUTPUT_FILE_SHA256 = 4918d0e4a62feb13d74a68a4d111d054935e880ad4ac50b8a54adcd222918a95

## Machine validation
- VISUAL_CLIP_ORDER = PASS (A->C->B->D avg-RGB verified at clip midpoints)
- AUDIO: gain 0.4 -> RMS -29.1dB (exact), mute -> -82.2dB, balance full-left (R -inf) = PASS
- AV_SYNC_SANITY = PASS (0.0s boundary deviation)
- FAILURE_DEBUG_PATH = PASS (missing input traceable at execution boundary)
- TRIM/MOVE/DELETE = PASS (output duration + order prove semantics reached bytes)

## Version / provenance (Version Governance exercised)
PlatformReleaseVersion 0.1.0; BuildIdentity d77fe84d; CapabilityId media.render @1.0;
Implementation media.render.ffmpeg 1.0.0; Plugin 1.0.0; FFmpeg 7.0.2; ReleaseChannel DEV;
ConfigurationDigest (filters) recorded; TraceId frmc-20260815-0001; model/worker/rollout
sections ABSENT (not applicable). EXECUTION_IS_PINNED = YES (no dynamic latest).

## Tests / gates
- FirstRealMediaCutTest 5 PASS (exact time/hash determinism/immutable pins/audio semantics/
  version identity)
- full suite / drift / bootJar / pfirr1: see FCV evidence + final report
- no TemporalMapping | no AI/CV/ASR | no /api/v1 reintroduced | FFmpeg execution-only

## Deferred
Output Artifact persistence (current path gap, file digest preserved; no second Artifact
authority); OASDIFF_CHECKSUM_PIN hardening; NEXT_ACTION = TEMPORAL_MAPPING_FOUNDATION_V1.
