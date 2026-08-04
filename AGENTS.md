# AGENTS.md - Repository Agent Governance

You are working in the `media-platform` repository.

This file defines durable repository-local governance for coding agents. It is not a task brief. Task-specific commands, acceptance gates, branch names, worktree paths, commit SHAs, queues, evidence locations, and runtime session details belong in the current Owner task record and external evidence package, not in this permanent instruction file.

## Instruction Precedence

1. Follow system and developer instructions first.
2. Follow the latest explicit Owner/user task authorization next.
3. Apply repository-local instruction files as durable repository rules within their scope.
4. If repository-local instructions conflict with a newer explicit Owner task, stop, record the conflict, apply the higher-priority authorization, and schedule instruction alignment as a separate governance change.

Before work, inspect all applicable instruction files from the repository root to the target path. Record their scope, conflicts, and precedence in the task evidence when the task is governed or safety-sensitive.

## Repository State and Worktree Governance

Use one owned branch and one linked worktree per active task unless the Owner explicitly authorizes another topology.

Do not perform feature development directly in the canonical `main` root. Keep canonical `main` clean except during an explicitly authorized serialized integration operation.

Freeze an exact candidate SHA before verification. Any commit or history change after verification requires renewed verification.

Integrate accepted candidates through fast-forward only unless the Owner explicitly authorizes another strategy.

After successful post-integration verification on `main`, retire completed task branches and linked worktrees with normal Git commands. Preserve dirty, unique, or Owner-created work before cleanup or retirement.

Do not use `git reset --hard`, `git clean`, wildcard deletion, force branch deletion, destructive batch cleanup, manual ref updates, rebases, squashes, or cherry-picks unless the current Owner task specifically authorizes the operation and the evidence first proves preservation and scope.

Do not fetch, pull, push, publish, deploy, or mutate remote refs unless the current task explicitly authorizes remote operations.

## Change Scope Discipline

Keep each task within its authorized scope. Do not expand a documentation, governance, test, or feature task into unrelated cleanup.

Do not modify production source, test source, build files, application configuration, database migrations, or runtime behavior unless the current task explicitly authorizes those changes.

Do not weaken tests, disable tests, bypass security checks, expose internal storage paths, or add temporary architecture exceptions to pass a gate.

Use repository documentation and current repository state as source material. Inspect existing code and conventions before adding abstractions.

## Evidence and Verification

For governed tasks, record:

- current branch, HEAD, worktree, status, and stash state;
- applicable instruction files and conflicts;
- exact changed paths and scope classification;
- exact commands and results for required gates;
- final candidate SHA and parent;
- post-integration main state when integration is authorized.

Use machine-readable test reports when reporting test counts. Verify arithmetic for total, passed, failures, errors, and skipped.

A summary claim is not a substitute for a manifested artifact, command output, diff, or machine-readable report when the task requires evidence.

## Secrets and Evidence Safety

Do not place credentials, secrets, private environment dumps, or hidden reasoning in repository files or evidence.

Evidence should contain enough command output, hashes, and file paths to verify the result without embedding credentials or unrelated private data.

## Nested Instructions

Nested instruction files may add narrower rules for their directory tree. They must not contradict root safety and governance rules.

Retain legitimate directory-specific rules. Remove stale task-specific directives when they become active conflicts. Do not create nested instruction files merely for completeness.
