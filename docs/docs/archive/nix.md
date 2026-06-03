# Nix and Local Development

> **Document Navigation**: [docs/README.md](./README.md). CI uses a fixed JDK, see `.github/workflows/ci.yml` at the repository root.

This repository uses **Java 25** as the Gradle toolchain (see `JavaLanguageVersion.of(25)` in the root [`build.gradle.kts`](../build.gradle.kts)). **Nix** is an **optional** tool for providing a reproducible local development environment. It complements the **Gradle Wrapper** and **Foojay Toolchains**, not replaces them.

---

## 1. What is Nix?

Nix is a powerful package manager and build system that provides:

- **Reproducible environments**: Same tools and dependencies across all developer machines
- **Declarative configuration**: Environment defined in `flake.nix`
- **Isolation**: Tools are installed without affecting the host system
- **Cross-platform**: Works on any Linux distribution and macOS

**Important**: NixOS is **NOT** required. The Nix package manager works on any Linux or macOS system.

---

## 2. Installation

Install the Nix package manager following the official instructions:

- **Official**: https://nixos.org/download.html
- **Determinate Nix Installer** (recommended): https://github.com/DeterminateSystems/nix-installer

After installation, enable flakes (if not already enabled):

```bash
mkdir -p ~/.config/nix
echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf
```

---

## 3. Recommended Workflow

1. **Enter the development shell**:

   ```bash
   nix develop
   ```

   This will download and set up JDK 25, Gradle, OpenTofu, and Docker client.

2. **Verify the environment**:

   ```bash
   java -version
   ./gradlew -version
   ```

3. **Build and test** (Gradle Wrapper remains the authoritative entry point):

   ```bash
   ./gradlew test
   ```

4. **Exit the shell**:

   ```bash
   exit
   ```

---

## 4. Flake Validation

To validate the flake configuration without entering the shell:

```bash
nix flake check
```

This checks that the flake is syntactically correct and all inputs are available.

---

## 5. Root Directory `flake.nix`

The repository root contains a `flake.nix` file that defines the development environment. It provides:

| Tool | Purpose | Notes |
|------|---------|-------|
| JDK 25 | Java development | Matches Gradle toolchain |
| Gradle | Build tool | For wrapper use only |
| OpenTofu | IaC tool | For infrastructure management |
| Docker | Container client | For interacting with Docker daemon |
| git, curl, jq | Utilities | Common development tools |

---

## 6. Relationship with Gradle / CI

| Layer | Purpose |
|-------|---------|
| **Nix** | Reproducible local development environment |
| **Gradle Wrapper** (`./gradlew`) | Unified Gradle version, **everyone should use wrapper for builds** |
| **Java Toolchains** (`build.gradle.kts`) | Build and test language version **25**; Foojay can auto-resolve if JDK 25 is not installed |
| **GitHub Actions** | Uses `actions/setup-java` to fix **Temurin 25**, **does not depend** on Nix |

**Conclusion**: Production and CI do **not** use Nix; Nix is an **optional layer** for improving local development experience.

---

## 7. Tradeoffs vs asdf and SDKMAN

| Feature | Nix | asdf | SDKMAN |
|---------|-----|------|--------|
| **Scope** | Full environment (any package) | Multi-language via plugins | JVM-focused |
| **Reproducibility** | High (flake.lock pins all dependencies) | Medium (plugin-dependent) | Medium |
| **Isolation** | Strong (Nix store) | Weak (modifies PATH) | Weak (modifies PATH) |
| **Learning Curve** | Steep | Gentle | Gentle |
| **Cross-platform** | Linux, macOS | Linux, macOS, Windows (WSL2) | Linux, macOS, Windows (WSL2) |
| **Disk Usage** | Higher (Nix store) | Lower | Lower |
| **Speed** | Slower first use (downloads) | Faster | Faster |

**Recommendation**:

- Use **Nix** if you want a fully reproducible environment and are comfortable with its concepts
- Use **asdf** if you prefer a simpler, plugin-based approach (see [`asdf-vm.md`](./asdf-vm.md))
- Use **SDKMAN** if you primarily work with JVM tools (see [`sdkman.md`](./sdkman.md))

---

## 8. Optional Alternatives

- **[asdf](https://asdf-vm.com/)**: Multi-language version manager, can also install JDK 25 (see [`asdf-vm.md`](./asdf-vm.md))
- **[SDKMAN!](https://sdkman.io/)**: JVM-focused SDK management (see [`sdkman.md`](./sdkman.md))
- **[mise](https://mise.jdx.dev/)**: Similar to asdf, some workflows can migrate

---

## 9. Common Questions

- **Windows**: Native Nix support is limited. Use **WSL2 (Linux)** or **IDE built-in JDK + Gradle Toolchains**.
- **Docker Development**: JDK inside containers is determined by the **image**; Nix only affects the host command line.
- **Version Upgrades**: When upgrading JDK patches, update `flake.nix`, run `nix flake update`, and verify with `./gradlew test`.
- **Disk Space**: Nix stores all packages in `/nix/store`. Use `nix-collect-garbage` to clean up unused packages.
- **CI/CD**: Nix is **not** used in CI. CI uses `actions/setup-java` with Temurin 25.
