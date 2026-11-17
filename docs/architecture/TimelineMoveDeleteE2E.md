# Timeline Move/Delete — Real E2E (Chrome → FFmpeg → Download)

**Task:** `MEDIA_PLATFORM_TIMELINE_MOVE_DELETE_PRODUCT_R1`
**Run:** `evidence/l3-move-delete-20261117T1650Z` (final candidate) (harness: `scripts/dev/first-render --timeline-operation-editing --nle-browser`)
**Result:** `FIRST_RENDER_LOCAL_VALIDATION_PASS`

```text
INITIAL_REVISION_ID:        31adc01b-4061-4719-b935-ab06b543acf3 (after clearing the harness seed)
INITIAL_SNAPSHOT_DIGEST:    6b1a4f5e2530…

TRIM_OPERATION_REVISION:    e4f6709e-8724-4627-8a1a-f9c98feb255f
TRIM_SNAPSHOT_DIGEST:       cb72e728e591…   (clip-c 4/1–5/1)
DELETE_OPERATION_REVISION:  dd8ce24f-21b5-4fe2-a5cc-46274eb11104
DELETE_SNAPSHOT_DIGEST:     e88ba90c79bc…   (clips: clip-a, clip-c)
MOVE_OPERATION_REVISION:    86a9e1d8-3461-4f34-b2c4-72526f4d454a
MOVE_SNAPSHOT_DIGEST:       bc77ec18687c…   (clip-c 2/1–3/1; clip ID, source 4/1–5/1 unchanged)

RENDER_JOB_ID:              rj_c1d140ba7a5a494d9c9e5fe98d35b732
PROVIDER_ID:                ffmpeg (FFmpeg PF4J provider, formal RenderJob)
BOUND_GRAPH_IDENTITY:       not surfaced by the product API evidence; the formal RenderJob for the
                            move revision was accepted with the FFmpeg provider pin and completed
                            (provider binding remains an internal #22 concern)
ARTIFACT_ID:                render-task-0b499e9f9263a6e04309d617629556d1df7f7353828f8c9f13b9
DELIVERY_ID:                dlv_1e8f0a1c1e914884815b24358dc3a1ad
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

- `logs/browser-result-final.json` — stages, requests, revisions, digests, negatives, media probe.
- `logs/move-delete-output-final.mp4` — downloaded product output (sha256 above).
- `logs/move-delete-final.png` — final editor state.
- `logs/first-render.log` — harness run (`FIRST_RENDER_LOCAL_VALIDATION_PASS`).
- `l3-move-delete-20261117T1650Z/` — full harness output (build, tests, runtime, product result).

## What this proves

- Move keeps the clip identity, track, source trim and duration; only the timeline placement changes.
- Delete removes exactly the target clip; the other clip keeps its position; source media is retained.
- The edits survive a page refresh because they are canonical revisions.
- The formal render consumes the edited snapshot: the output ordering, duration and pixel content match
  the edited timeline, and the deleted clip is absent.
