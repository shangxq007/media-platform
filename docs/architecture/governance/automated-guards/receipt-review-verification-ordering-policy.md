# Receipt Review-Verification Ordering Policy

## Rules

1. Review receipt must exist before verifier starts
2. Review receipt must have `decision: PASS`
3. Review `completed_at` must be before verifier `started_at`
4. Review `subject_identifier` must match verification `subject_identifier`
5. Both `run_id` and `worktree` must be non-empty, exact paths, no wildcards
6. `decision` must be `PASS` or `FAIL` — no `CONDITIONAL_PASS`

## Error Codes

| Code | Description |
|------|-------------|
| REVIEW_RECEIPT_MISSING | Review receipt file not found |
| REVIEW_RECEIPT_CREATED_TOO_LATE | Review completed after verifier started |
| REVIEW_SUBJECT_MISMATCH | Subject identifiers don't match |
| WILDCARD_WORKTREE | Worktree contains * or <placeholder> |
| EMPTY_RUN_ID | Run ID is empty |
| INVALID_DECISION | Decision not in allowed enum |
