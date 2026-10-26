# Secret bootstrap

`bootstrap` is the single Bitwarden Secrets Manager launcher. It accepts only a
fixed profile name (`api`, `worker`, `combined`, `flyway`, `jooq`); the profile
maps to a trusted allowlist path, an approved target entry point, and the
profile's required non-secret runtime settings.

## Boundaries

- **Read scope:** `bws secret list <project>` returns the project's secret set;
  allowlisting does not reduce what the upstream Project/Machine Account can read.
  Use a dedicated Project and read-only Machine Account to limit read scope.
- **Injection scope:** only allowlisted names are passed to the target process.
- **Non-secret runtime:** `SPRING_PROFILES_ACTIVE`, `PLATFORM_ENV`, and the jOOQ
  image/library/version/staging variables are preserved from the supervisor.
- Bootstrap token, project/server identity, Neon admin key, and other management
  material are never placed in the target environment.
- No shell, no argv secret, no secret file/cache, no `bws run`.
- Missing/malformed/duplicate/empty/dangerous values, unknown profile, wrong
  target, untrusted allowlist path/owner/mode/symlink, missing binary, error or
  timeout all fail closed.

The old `acceptance` profile is intentionally absent. Database acceptance is
launched only by the control-plane `scripts/testinfra/lifecycle.py` supervisor,
which does not pass BWS or Neon management credentials to the test process.

The actual CLI version/hash and Machine Account/Project read permissions remain
deployment prerequisites; they are not asserted by this code alone.

The lifecycle's local offline invocation is `scripts/test-infra fake --output <new-external-directory>`.
Its anonymous-pipe handoff is separate from these five bootstrap profiles; there
is no `acceptance` profile or Bitwarden fallback. Real Supervisor admission remains
blocked on separate OS/CI security principals and credential-scope verification.
