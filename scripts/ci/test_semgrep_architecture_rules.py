#!/usr/bin/env python3
"""Contract tests for the corrected inherited Semgrep architecture rules."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / ".semgrep/media-platform-architecture.yml"
SEMGREP = ("uvx", "semgrep", "--metrics", "off")

REMOTION_RULE = "arch-no-remotion-production"
ARTIFACT_DAG_RULE = "arch-no-artifact-dag-runtime"
PROVIDER_KEY_RULE = "arch-no-provider-key-exposure"

EXPECTED_FINDINGS = {
    "platform-app/src/main/java/contract/RemotionProductionPositive.java": REMOTION_RULE,
    "render-module/src/main/java/contract/ArtifactDAGRuntimePositive.java": ARTIFACT_DAG_RULE,
    "platform-app/src/main/java/contract/ProviderSecretResponse.java": PROVIDER_KEY_RULE,
    "platform-app/src/main/java/contract/ProviderBearerResponse.java": PROVIDER_KEY_RULE,
}

NEGATIVE_CASES = (
    "platform-app/src/main/java/contract/RemotionNegative.java",
    "render-module/src/main/java/contract/ArtifactDAGNegative.java",
    "platform-app/src/main/java/contract/ProviderTokenTypeResponse.java",
)

FIXTURES = {
    "platform-app/src/main/java/contract/RemotionProductionPositive.java": """
        package contract;
        final class RemotionProductionPositive {
          void dispatch(ProviderStatus status, Job job) {
            if (status == ProviderStatus.PRODUCTION) {
              new RemotionRenderer().render(job);
            }
          }
        }
    """,
    "platform-app/src/main/java/contract/RemotionNegative.java": """
        package contract;
        final class RemotionNegative {
          void dispatch(ProviderStatus status, Job job) {
            RemotionRenderer renderer = new RemotionRenderer();
            renderer.render(job);
            if (status == ProviderStatus.POC) {
              new RemotionRenderer().render(job);
            }
            if (status == ProviderStatus.STUB) {
              RemotionDispatcher.dispatch(job);
            }
          }
        }
    """,
    "render-module/src/main/java/contract/ArtifactDAGRuntimePositive.java": """
        package contract;
        final class ArtifactDAGRuntimePositive {
          void activate() {
            new ArtifactDAGRuntime().activate();
          }
        }
    """,
    "render-module/src/main/java/contract/ArtifactDAGNegative.java": """
        package contract;
        final class ArtifactDAGNegative {
          ArtifactDAGImpact describe() {
            return new ArtifactDAGImpact("documentation-only value");
          }
        }
    """,
    "render-module/src/test/java/contract/ArtifactDAGTestFixture.java": """
        package contract;
        final class ArtifactDAGTestFixture {
          void exerciseDeferredRuntime() {
            new ArtifactDAGRuntime().activate();
          }
        }
    """,
    "platform-app/src/main/java/contract/ProviderSecretResponse.java": """
        package contract;
        final class ProviderSecretResponse {
          void respond(java.util.Map<String, String> result) {
            result.put("apiKey", "sk-contract-example-12345");
          }
        }
    """,
    "platform-app/src/main/java/contract/ProviderBearerResponse.java": """
        package contract;
        final class ProviderBearerResponse {
          void respond(java.util.Map<String, String> result) {
            result.put("authorization", "Bearer tp-contract-example-12345");
          }
        }
    """,
    "platform-app/src/main/java/contract/ProviderTokenTypeResponse.java": """
        package contract;
        final class ProviderTokenTypeResponse {
          void respond(java.util.Map<String, String> result) {
            result.put("tokenType", "Bearer");
          }
        }
    """,
}

MALFORMED_RULES = {
    REMOTION_RULE: """    pattern: |
      if ($STATUS == ProviderStatus.PRODUCTION) {
        ...
        Remotion...
        ...
      }
""",
    ARTIFACT_DAG_RULE: """    pattern: |
      class $CLASS {
        ...
        ArtifactDAG...
        ...
      }
""",
}


def run(command: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            cwd=cwd,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=180,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise AssertionError(f"command failed closed: {' '.join(command)}: {exc}") from exc


def command_evidence(completed: subprocess.CompletedProcess[str]) -> str:
    return (
        f"exit={completed.returncode}\n"
        f"stdout:\n{completed.stdout}\n"
        f"stderr:\n{completed.stderr}"
    )


def validate(config: Path, *, should_pass: bool) -> None:
    completed = run([*SEMGREP, "--validate", "--config", str(config)], ROOT)
    if should_pass:
        if completed.returncode != 0:
            raise AssertionError(
                f"Semgrep config validation failed:\n{command_evidence(completed)}"
            )
        return

    combined = completed.stdout + completed.stderr
    validation_markers = ("Pattern parse error", "Invalid pattern", "invalid pattern")
    if completed.returncode == 0 or not any(marker in combined for marker in validation_markers):
        raise AssertionError(
            "malformed representative did not fail with a pattern validation error:\n"
            + command_evidence(completed)
        )


def replace_rule_match(config_text: str, rule_id: str, malformed: str) -> str:
    rule_start = config_text.find(f"  - id: {rule_id}\n")
    if rule_start < 0:
        raise AssertionError(f"missing rule {rule_id}")
    message_start = config_text.find("    message:", rule_start)
    if message_start < 0:
        raise AssertionError(f"missing message for rule {rule_id}")
    next_rule = config_text.find("\n  - id:", rule_start + 1)
    if next_rule >= 0 and message_start > next_rule:
        raise AssertionError(f"malformed rule structure for {rule_id}")
    definition_start = rule_start + len(f"  - id: {rule_id}\n")
    return config_text[:definition_start] + malformed + config_text[message_start:]


def assert_malformed_rejections(config_text: str, temp_root: Path) -> None:
    for rule_id, malformed in MALFORMED_RULES.items():
        mutated = temp_root / f"{rule_id}.yml"
        mutated.write_text(replace_rule_match(config_text, rule_id, malformed))
        validate(mutated, should_pass=False)


def write_fixtures(temp_root: Path) -> None:
    for relative_path, source in FIXTURES.items():
        target = temp_root / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.strip() + "\n")


def scan_fixtures(temp_root: Path) -> dict[str, list[str]]:
    completed = run(
        [*SEMGREP, "--json", "--config", str(CONFIG), "."],
        temp_root,
    )
    if completed.returncode != 0:
        raise AssertionError(f"Semgrep fixture scan failed:\n{command_evidence(completed)}")
    try:
        report = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise AssertionError(
            f"Semgrep fixture scan returned unparseable JSON:\n{command_evidence(completed)}"
        ) from exc
    if not isinstance(report, dict) or not isinstance(report.get("results"), list):
        raise AssertionError("Semgrep JSON report is missing its results array")
    if report.get("errors"):
        raise AssertionError(f"Semgrep fixture scan reported errors: {report['errors']!r}")

    findings: dict[str, list[str]] = {}
    for result in report["results"]:
        if not isinstance(result, dict):
            raise AssertionError(f"unexpected Semgrep result shape: {result!r}")
        path = result.get("path")
        rule_id = result.get("check_id")
        if not isinstance(path, str) or not isinstance(rule_id, str):
            raise AssertionError(f"Semgrep result lacks path/check_id: {result!r}")
        normalized = re.sub(r"^\./", "", path)
        known_rule_ids = (REMOTION_RULE, ARTIFACT_DAG_RULE, PROVIDER_KEY_RULE)
        canonical_rule_id = next(
            (
                known
                for known in known_rule_ids
                if rule_id == known or rule_id.endswith(f".{known}")
            ),
            rule_id,
        )
        findings.setdefault(normalized, []).append(canonical_rule_id)
    return findings


def assert_matrix(findings: dict[str, list[str]]) -> None:
    expected = {path: [rule_id] for path, rule_id in EXPECTED_FINDINGS.items()}
    if findings != expected:
        raise AssertionError(f"unexpected fixture findings: actual={findings!r}, expected={expected!r}")
    for path in NEGATIVE_CASES:
        if path in findings:
            raise AssertionError(f"negative fixture unexpectedly matched: {path}: {findings[path]}")


def main() -> int:
    config_text = CONFIG.read_text()
    validate(CONFIG, should_pass=True)
    with tempfile.TemporaryDirectory(prefix="semgrep-architecture-contract-") as temp_dir:
        temp_root = Path(temp_dir)
        assert_malformed_rejections(config_text, temp_root)
        write_fixtures(temp_root)
        findings = scan_fixtures(temp_root)
        assert_matrix(findings)

    print(
        "SEMGREP_ARCHITECTURE_CONTRACT_MATRIX=PASS "
        "VALIDATE=1/1 MALFORMED_REJECTIONS=2/2 "
        "POSITIVE_CASES=4/4 POSITIVE_FINDINGS=4 "
        "NEGATIVE_CASES=3/3 NEGATIVE_FINDINGS=0 UNEXPECTED_FINDINGS=0"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"SEMGREP_ARCHITECTURE_CONTRACT_MATRIX=FAIL {exc}", file=sys.stderr)
        raise SystemExit(1)
