# H7 production input boundary

Run from the repository top level:

```sh
python3 -B scripts/guards/h7-architecture-guard.py --root "$PWD" --tree HEAD --receipt /absolute/external/new-receipt.json
python3 -B scripts/guards/h7-architecture-guard.py --root "$PWD" --tree HEAD --self-test
bash scripts/check-architecture-drift.sh
```

The ordinary architecture entrypoint uses its working repository root and `HEAD` by default. For a reviewed staged candidate, explicitly stage the intended files and bind the resolved `git write-tree` result using `H7_SOURCE_TREE=<tree>` (in the isolated candidate repository only). `--tree` overrides this environment binding. HEAD is resolved once to a tree; no remote operation is performed. There is no recursive fallback or ancestor-root search.

`h7_input_boundary.Snapshot` is the sole membership authority: `git ls-tree` of the resolved tree. Required settings.gradle.kts context and eligible tracked production Java bytes must equal their named Git blobs. Missing files, invalid/duplicate paths, root mismatch, symlinks, unsupported object types/formats and drift reject. A different candidate content set requires a different explicit tree, not unchecked working-directory bytes. Untracked nested copies are not members regardless of their directory names; a legitimately tracked name is not blacklisted. Existing module/source filters then select the H7 graph without changing parsing or rules.

The optional exclusively created receipt records the resolved tree, every eligible raw-byte digest, selected paths, and selected decoded-text digests. UTF-8 universal newline conversion is retained from the original guard. Evaluation consumes the captured text, and input revalidation follows evaluation. Bounded byte comparisons do not claim continuous filesystem immutability or identify a writer. Concurrent editing during verification is unsupported and detected drift rejects; external preservation monitoring is distinct.

Run focused controls with `python3 -B scripts/guards/test_h7_input_boundary.py`. Historical source cardinalities belong in regression evidence, not implementation. No frozen external census/adapter/comparator is installed as a competing verifier.
