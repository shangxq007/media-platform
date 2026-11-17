# Timeline Move/Delete — Real E2E (Chrome → FFmpeg → Download)

**Task:** `MEDIA_PLATFORM_TIMELINE_MOVE_DELETE_PRODUCT_R1`
**Run:** `evidence/l3-move-delete-20261117T1620Z` (harness: `scripts/dev/first-render --timeline-operation-editing --nle-browser`)
**Result:** `FIRST_RENDER_LOCAL_VALIDATION_PASS`

```text
INITIAL_REVISION_ID:        475732a9-53ea-4953-b073-ebdb3cf69174 (after clearing the harness seed)
INITIAL_SNAPSHOT_DIGEST:    6b1a4f5e25301f3159fbe4b7f0b7e3490e65ab8b77138b463c6169a7482776be

TRIM_OPERATION_REVISION:    5b3620cf-d039-467e-9663-5437399ccfa2
TRIM_SNAPSHOT_DIGEST:       971a5a4e48e94d433a9d5a90a0a2893092399d17863da48882d0afbf73813dcc   (clip-c 4/1–5/1)
DELETE_OPERATION_REVISION:  f678982e-16ac-472a-975e-be3e1b197f46
DELETE_SNAPSHOT_DIGEST:     f665e05a37f9…   (clips: clip-a, clip-c)
MOVE_OPERATION_REVISION:    7cd15b0b-66b3-43d8-9418-bb3280ad3b1a
MOVE_SNAPSHOT_DIGEST:       d75e0192cc93…   (clip-c 2/1–3/1; clip ID, source 4/1–5/1 unchanged)

RENDER_JOB_ID:              rj_7ec066a4986040cab8095a0b9fa58379
PROVIDER_ID:                ffmpeg (FFmpeg PF4J provider, formal RenderJob)
BOUND_GRAPH_IDENTITY:       formal #20→#21→#22 binding for revision 7cd15b0b (harness product result)
ARTIFACT_ID:                render-task-624977441f9d5a95acb20cb81e56cb8bc7a740a369953f1c6384
DELIVERY_ID:                dlv_6b5b529c0fb2480d819f35fd57b77678
OUTPUT_DURATION:            3.0 s (h264, 320×240, 24/1)
OUTPUT_SHA256:              29fba4bcfab03425c2a14a558aeb7a022b4e757e300ee2ac35efac0e58794ae6
FRAME_SAMPLING_RESULT:      red @0.4s and @1.6s (clip A), blue @2.2s and @2.8s (moved clip C),
                            probe @1.0s red (the deleted green clip never appears)
E2E_RESULT:                 PASS
```

## Chrome steps performed

1. Open `/w/workspace-fr-e2e/projects/project-fr-e2e/edit`; verify the authenticated workspace/project scope.
2. Reject an invalid upload (`unsupported source MIME`); confirm the source endpoint is `401` without a token.
3. Upload the synthetic 6 s source: red 0–2 s, green 2–4 s, blue 4–6 s.
4. Clear the harness-seeded operation-editing clips through formal `DELETE_CLIP` operations.
5. Create three adjacent clips through the formal add-media-clip **preview + apply** operations:
   `clip-a` src 0–2 @ 0–2, `clip-b` src 2–4 @ 2–4, `clip-c` src 4–6 @ 4–6.
6. **Trim** clip-c by dragging its right edge −1 s (preview shows source 4/1 → 5/1), then Apply trim.
7. **Delete** clip-b through the explicit *Delete selected clip* editor entry; the canonical snapshot
   no longer contains clip-b. A stale MOVE on the deleted clip is rejected (404).
8. **Move** clip-c by dragging its body −2 s (absolute start 2/1, non-ripple); the editor sends
   preview then apply with the same command id.
9. Reload the page: HEAD is the move revision and the strip/snapshot match the applied edits.
10. Submit a formal RenderJob for 0–3000 ms; wait for COMPLETED; create a Delivery; download the output.
11. Probe the download: decodes cleanly, 3.0 s, red 0–2 s then blue 2–3 s, no green.
12. Negative: delivery download without a token is `401`.

## Evidence files

- `logs/browser-result.json` — stages, requests, revisions, digests, negatives, media probe.
- `logs/move-delete-output.mp4` — downloaded product output (sha256 above).
- `logs/move-delete-final.png` — final editor state.
- `logs/first-render.log` — harness run (`FIRST_RENDER_LOCAL_VALIDATION_PASS`).
- `l3-move-delete-20261117T1620Z/` — full harness output (build, tests, runtime, product result).

## What this proves

- Move keeps the clip identity, track, source trim and duration; only the timeline placement changes.
- Delete removes exactly the target clip; the other clip keeps its position; source media is retained.
- The edits survive a page refresh because they are canonical revisions.
- The formal render consumes the edited snapshot: the output ordering, duration and pixel content match
  the edited timeline, and the deleted clip is absent.
