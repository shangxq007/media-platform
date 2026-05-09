# Autonomous Execution Rules

The development computer may grant Roo Code broad command execution permission because this is a brand-new project without secrets. Even so, Roo Code must behave conservatively.

Allowed behavior:

- Inspect repository structure.
- Create and edit files in the workspace.
- Run builds, tests, formatters, code generators, Docker local validation, OpenTofu/Terraform validation, and local smoke tests.
- Install project-declared local development dependencies when the user environment allows it.
- Create documentation of missing environment requirements.

Forbidden behavior:

- Do not push to remote.
- Do not run production deployment.
- Do not run destructive infrastructure commands.
- Do not delete user work.
- Do not modify files outside the workspace.
- Do not add real credentials, tokens, private keys, or cloud secrets.
- Do not hide failures.

Execution log:

Maintain `docs/roo-execution-log.md` with:

- task name
- files inspected
- files changed
- commands run
- tests run
- failures and fixes
- assumptions
- remaining TODOs

Environment/resource discovery:

Whenever implementation discovers a required external capability, update `deployment-prep/environment-resource-requirements.md` with:

- resource name
- purpose
- local development substitute
- deployment requirement
- required configuration variables
- network/storage/compute assumptions
- whether credentials are required later
- blocking or non-blocking status

Stop and ask only when:

- Requirements conflict.
- A destructive migration is unavoidable.
- Paid services or real production credentials are required.
- The user must choose between incompatible architecture paths.

Otherwise make the simplest reversible implementation choice and document it.
