#!/usr/bin/env python3
"""Platform-owned, fail-closed change-impact classification for CI policy."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable


CATEGORY_ORDER = (
    "governance",
    "docs",
    "frontend",
    "backend_test",
    "backend_runtime",
    "build_graph",
    "container",
    "gitops",
    "semgrep",
    "formal_verification",
    "workflow",
    "ci_infrastructure",
    "unknown",
)

BACKEND_CATEGORIES = {"backend_test", "backend_runtime", "build_graph", "container"}
FULL_CI_CATEGORIES = {"workflow", "ci_infrastructure", "unknown"}
RUNTIME_IMAGE_INPUT_CATEGORIES = {"backend_runtime", "build_graph", "container"}

CI_SCRIPT_NAMES = {
    "check-api-contract-governance.sh",
    "check-architecture-drift.sh",
    "check-document-governance.sh",
    "check-storage-identity-placement-m0-m1.py",
    "final-review-validate.sh",
    "infra-validate.sh",
    "local-docker-test.sh",
    "local-test.sh",
    "roadmap20-source-completeness-gate.sh",
    "test-check-storage-identity-placement-m0-m1.py",
    "validate-production-readiness.sh",
    "verify-egress-smoke-config.sh",
    "verify-oidc-jwt.sh",
    "verify-pfirr1-jooq-authority-fail-closed.sh",
}

STORAGE_GOVERNANCE_CI_INFRASTRUCTURE_PATHS = {
    "scripts/guards/validate-storage-object-identity-placement-migration-contract.py",
}

JOOQ_CI_INFRASTRUCTURE_PATHS = {
    "scripts/test_verify_jooq_generated_schema_parity.py",
    "scripts/verify-jooq-generated-schema-parity.py",
    "typed-schema-module/regenerate-jooq-schema.sh",
}

JOOQ_BUILD_GRAPH_PATHS = {
    "typed-schema-module/jooq-codegen.xml",
}


def _under(path: str, prefix: str) -> bool:
    return path == prefix.rstrip("/") or path.startswith(prefix)


def _normalise_path(raw_path: str) -> str:
    path = raw_path.replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    pure = PurePosixPath(path)
    if (
        not path
        or path.startswith("/")
        or "\x00" in path
        or "\n" in path
        or "\r" in path
        or any(part in {"", ".", ".."} for part in pure.parts)
    ):
        raise ValueError(f"unsafe or empty repository path: {raw_path!r}")
    return pure.as_posix()


def classify_path(raw_path: str) -> tuple[str, ...]:
    """Return all applicable categories for one repository-relative path."""
    try:
        path = _normalise_path(raw_path)
    except ValueError:
        return ("unknown",)

    categories: set[str] = set()
    basename = PurePosixPath(path).name

    if _under(path, ".github/workflows/"):
        categories.add("workflow")
    elif _under(path, ".semgrep/") or path == ".semgrepignore":
        categories.add("semgrep")
    elif (
        path in {"AGENTS.md", ".hermes.md"}
        or _under(path, "docs/architecture/governance/")
        or _under(path, "docs/architecture/current/")
        or _under(path, "contracts/governance/")
    ):
        categories.add("governance")
    elif _under(path, "docs/") or ("/" not in path and path.lower().endswith(".md")):
        categories.add("docs")

    if _under(path, "frontend/"):
        categories.add("frontend")

    if _under(path, "formal/") or _under(path, "scripts/formal/"):
        categories.add("formal_verification")

    if "/src/test/" in f"/{path}" or _under(path, "scripts/test/"):
        categories.add("backend_test")

    if re.match(r"^[^/]+/src/main(?:/|$)", path) or _under(path, "platform-algorithms/"):
        categories.add("backend_runtime")

    if not _under(path, "frontend/") and (
        path in {"settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle", "gradle.properties", "gradlew", "gradlew.bat"}
        or path in JOOQ_BUILD_GRAPH_PATHS
        or _under(path, "gradle/")
        or basename in {"build.gradle.kts", "build.gradle"}
    ):
        categories.add("build_graph")

    if (
        basename == "Dockerfile"
        or basename.startswith("Dockerfile.")
        or path == ".dockerignore"
        or _under(path, "docker/")
        or path.startswith("docker-compose")
    ):
        categories.add("container")

    if (
        _under(path, "gitops/")
        or _under(path, "k8s/")
        or path in {
            "scripts/render-k8s-manifests.sh",
            "scripts/update-gitops-manifests.sh",
            "scripts/validate-production-readiness.sh",
            "scripts/verify-egress-smoke-config.sh",
        }
    ):
        categories.add("gitops")

    if (
        _under(path, "scripts/ci/")
        or _under(path, ".github/actions/")
        or (_under(path, "scripts/") and basename in CI_SCRIPT_NAMES)
        or path in STORAGE_GOVERNANCE_CI_INFRASTRUCTURE_PATHS
        or path in JOOQ_CI_INFRASTRUCTURE_PATHS
        or path in {".github/CODEOWNERS", ".pre-commit-config.yaml"}
    ):
        categories.add("ci_infrastructure")

    if not categories:
        categories.add("unknown")
    return tuple(category for category in CATEGORY_ORDER if category in categories)


@dataclass(frozen=True)
class Classification:
    paths: tuple[str, ...]
    path_categories: dict[str, tuple[str, ...]]
    categories: tuple[str, ...]
    reason: str

    @classmethod
    def from_paths(cls, paths: Iterable[str], reason: str = "git_diff") -> "Classification":
        unique_paths = tuple(dict.fromkeys(paths))
        if not unique_paths:
            return cls.fail_closed("empty_change_set")
        path_categories = {path: classify_path(path) for path in unique_paths}
        present = {category for values in path_categories.values() for category in values}
        categories = tuple(category for category in CATEGORY_ORDER if category in present)
        return cls(unique_paths, path_categories, categories, reason)

    @classmethod
    def fail_closed(cls, reason: str) -> "Classification":
        sentinel = f"<fail-closed:{reason}>"
        return cls((sentinel,), {sentinel: ("unknown",)}, ("unknown",), reason)

    def policy(self) -> dict[str, bool]:
        categories = set(self.categories)
        full_ci = bool(categories & FULL_CI_CATEGORIES)
        return {
            "full_ci": full_ci,
            "backend_ci": full_ci or bool(categories & BACKEND_CATEGORIES),
            "frontend_ci": full_ci or "frontend" in categories,
            "architecture_drift": full_ci
            or bool(categories & {"governance", "backend_runtime", "build_graph"}),
            "gitops_validation": full_ci or "gitops" in categories,
            "semgrep_validation": full_ci
            or bool(categories & {"backend_runtime", "build_graph", "semgrep"}),
            "formal_verification": full_ci or "formal_verification" in categories,
            "runtime_image_publish": bool(categories & RUNTIME_IMAGE_INPUT_CATEGORIES),
        }

    def as_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "reason": self.reason,
            "changed_path_count": len(self.paths),
            "paths": list(self.paths),
            "path_categories": {path: list(values) for path, values in self.path_categories.items()},
            "categories": list(self.categories),
            "policy": self.policy(),
        }


def changed_paths_from_git(root: Path, base: str, head: str) -> tuple[str, ...]:
    revision = re.compile(r"^[0-9a-fA-F]{7,40}$")
    if not revision.fullmatch(base) or not revision.fullmatch(head) or set(base) == {"0"}:
        raise ValueError("base/head must be non-zero Git revisions")
    result = subprocess.run(
        ["git", "diff", "--name-only", "-z", "--diff-filter=ACDMRTUXB", base, head, "--"],
        cwd=root,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"git diff failed: {detail or result.returncode}")
    return tuple(
        item.decode("utf-8", errors="surrogateescape")
        for item in result.stdout.split(b"\x00")
        if item
    )


def write_github_output(path: Path, classification: Classification) -> None:
    data = classification.as_dict()
    policy = classification.policy()
    outputs = {
        **{name: str(value).lower() for name, value in policy.items()},
        "categories": ",".join(classification.categories),
        "changed_path_count": str(data["changed_path_count"]),
        "reason": classification.reason,
    }
    with path.open("a", encoding="utf-8") as output:
        for name, value in outputs.items():
            output.write(f"{name}={value}\n")


def write_summary(path: Path, classification: Classification) -> None:
    policy = classification.policy()
    with path.open("a", encoding="utf-8") as summary:
        summary.write("## Change-impact CI policy\n\n")
        summary.write(f"Reason: `{classification.reason}`  \n")
        summary.write(f"Categories: `{', '.join(classification.categories)}`  \n")
        summary.write(f"Changed paths: `{len(classification.paths)}`\n\n")
        summary.write("| Decision | Run |\n|---|---|\n")
        for name, enabled in policy.items():
            summary.write(f"| `{name}` | `{'true' if enabled else 'false'}` |\n")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base")
    parser.add_argument("--head")
    parser.add_argument("--path", action="append", dest="paths", default=[])
    parser.add_argument("--paths-file", type=Path)
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--json", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = Path(__file__).resolve().parents[2]
    try:
        paths = list(args.paths)
        if args.paths_file:
            paths.extend(line for line in args.paths_file.read_text(encoding="utf-8").splitlines() if line)
        if paths:
            classification = Classification.from_paths(paths, "explicit_paths")
        elif args.base and args.head:
            classification = Classification.from_paths(
                changed_paths_from_git(root, args.base, args.head), "git_diff"
            )
        else:
            classification = Classification.fail_closed("missing_diff_range")
    except Exception as exc:  # The safe fallback is policy, not a skipped pipeline.
        classification = Classification.fail_closed(type(exc).__name__)
        print(f"change-impact classifier fell back to full CI: {exc}", file=sys.stderr)

    if args.github_output:
        write_github_output(args.github_output, classification)
    if args.summary:
        write_summary(args.summary, classification)
    if args.json or not args.github_output:
        print(json.dumps(classification.as_dict(), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
