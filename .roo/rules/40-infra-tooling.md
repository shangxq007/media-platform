# Infrastructure and Tooling

Toolchain goals:

- Prefer reproducible local development.
- Use Gradle Wrapper as the authoritative Gradle entry point.
- Use `.tool-versions` for asdf when practical.
- SDKMAN may be documented as an alternative for JDK users, but it must not replace Gradle Wrapper.
- Nix flakes or devshell may be added as an optional reproducible developer environment, not a mandatory path.
- Prefer OpenTofu for new IaC while keeping Terraform compatibility notes where useful.
- Keep Terraform/OpenTofu code isolated under `infra/` with clear README and non-production defaults.

IaC rules:

- No real cloud credentials in repo.
- No destructive default targets.
- No unattended `apply` or `destroy`.
- Provide examples and validation commands.
- Prefer `tofu fmt`, `tofu validate`, and documented backend/state choices.
- If Terraform support is retained, document command compatibility and migration caveats.
