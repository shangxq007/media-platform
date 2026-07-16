# Commit Graph Audit

## Technical Baseline

```
fba3c66980345392b8d486b7f343f4e9e38d4d92
```

## Original Chain (contaminated)

```
fba3c66 → c94778c → 9ce3c94 → 53cf1e7 → e150122 → 7f5f2b1 → db87ff8 → 5621f03 → 5b3babf → 733fb2a
                                                                                              ↑
                                                                                     V5 files added
                                                                                     then reverted
```

Commit `5621f03` introduced V5/RenderOutputCommit files. Commit `5b3babf` reverted them. While the final tree at `733fb2a` is evidence-only, the ancestry contains forbidden file commits.

## Clean Chain (authoritative)

```
fba3c669 → 4592e097
```

Single evidence-only commit extracted from `733fb2a` content. No forbidden files in ancestry.

## SHA Verification

```
53cf1e7 = 53cf1e75aec6cc4e389d0149d7cef847b47c6163 ✅
```

## Ancestry

```
fba3c669 ancestor of 4592e097: YES ✅
```

## Tree Diff

```
git diff --exit-code fba3c669 4592e097 -- . ':(exclude).agent-tasks/**'
EXIT 0: tree is evidence-only ✅
```

## Intermediate Commits

```
4592e097 docs: extract clean evidence chain from attestation addendum
```

No forbidden paths in any commit in the clean chain.

## Decisions

```
FULL_SHA_RESOLVED: YES
EXECUTABLE_TREE_UNCHANGED: YES
EVIDENCE_ONLY_DIFF_CONFIRMED: YES
CLEAN_CHAIN_ESTABLISHED: YES
```
