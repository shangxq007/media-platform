# V22 change ledger

| Date | Change | Authority | Data impact | Validation |
|---|---|---|---|---|
| 2026-09-25 | Added versioned Artifact upload, subject, projection/search, source-reference, retrieval ports and fail-closed validators | artifact-module | None; no schema or migration change | ArtifactContractFoundationTest PASS |
| 2026-09-25 | Added deterministic contract fingerprint/canonical JSON rules | artifact-module | None | Nested object/array/null regression PASS |
| 2026-09-25 | Recorded consumer migration boundaries | governance/review docs | None | Documentation and diff checks |
