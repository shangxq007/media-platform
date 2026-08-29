#!/usr/bin/env python3
"""Deterministic tests for the Phase 19 governance placeholder guard."""

from __future__ import annotations

import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/phase19-governance-placeholder-guard.py"
CAPABILITY_LEDGER = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-legacy-render-ffmpeg-functional-capability-ledger-v1.json"
)
CLEAN_FORWARD_LEDGER = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-render-zero-awareness-clean-forward-path-ledger-v2.json"
)
CORRECTION = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-render-module-ffmpeg-zero-awareness-correction.md"
)
RECONCILIATION = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-capability-disposition-reconciliation-v1.json"
)
TEST_SURFACE_ACCOUNTING = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-test-surface-change-accounting-v1.json"
)
SEMGREP_TARGET_ACCOUNTING = Path(
    "docs/architecture/governance/"
    "roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json"
)
NEW_JSON_TARGETS = (
    RECONCILIATION,
    TEST_SURFACE_ACCOUNTING,
    SEMGREP_TARGET_ACCOUNTING,
)
TARGETS = (
    CAPABILITY_LEDGER,
    CLEAN_FORWARD_LEDGER,
    CORRECTION,
    *NEW_JSON_TARGETS,
)
HISTORICAL_IDENTIFIER = "PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT"
NEW_ARTIFACT_UNRESOLVED_VALUES: tuple[tuple[str, str], ...] = (
    ("todo", "TODO"),
    ("tbd", "TBD"),
    ("placeholder", "PLACEHOLDER"),
    ("fill-me", "<fill-me>"),
    ("masked", "***"),
)

JSON_NEGATIVE_CONTROLS: tuple[tuple[str, str], ...] = (
    ("todo-entire", "TODO"),
    ("tbd-case-insensitive", "tbd"),
    ("fixme-punctuation", "FIXME..."),
    ("todo-all-punctuation", "TODO ()"),
    ("placeholder-entire", "PLACEHOLDER"),
    ("xxx-assignment-like", "XXX:  "),
    ("fill-me", "<fill-me>"),
    ("masked-required-value", "***"),
    ("masked-required-value-long", "********"),
)

MARKDOWN_NEGATIVE_CONTROLS: tuple[tuple[str, str], ...] = (
    ("todo-standalone", "TODO\n"),
    ("todo-star-list-standalone", "* TODO\n"),
    ("tbd-assignment", "decision = TBD\n"),
    ("tbd-assignment-punctuation", "decision = TBD []\n"),
    ("fixme-list-value", "- owner: FIXME...\n"),
    ("placeholder-table-value", "| owner | PLACEHOLDER |\n"),
    ("xxx-field-value", "status: xxx\n"),
    ("fill-me-field-value", "owner: <fill-me>\n"),
    ("masked-field-value", "required: ***\n"),
    ("todo-table-value", "| status | TODO |\n"),
)

# These fail-closed mutations are counted alongside the token matrix.
OTHER_NEGATIVE_CONTROL_COUNT = (
    6
    + 4
    + len(NEW_JSON_TARGETS) * (len(NEW_ARTIFACT_UNRESOLVED_VALUES) + 2)
)
NEGATIVE_CONTROL_COUNT = (
    len(JSON_NEGATIVE_CONTROLS)
    + len(MARKDOWN_NEGATIVE_CONTROLS)
    + OTHER_NEGATIVE_CONTROL_COUNT
)


def run_guard(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(GUARD), "--root", str(root)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )


def write_text(root: Path, relative_path: Path, content: str) -> None:
    destination = root / relative_path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(content, encoding="utf-8")


def write_json(root: Path, relative_path: Path, value: object) -> None:
    write_text(
        root,
        relative_path,
        json.dumps(value, indent=2, sort_keys=True) + "\n",
    )


def create_clean_fixture(root: Path) -> None:
    write_json(
        root,
        CAPABILITY_LEDGER,
        {
            "capabilities": [
                {
                    "CapabilityKey": HISTORICAL_IDENTIFIER,
                    "LegacyBehavior": "Return placeholder output for history",
                    "Notes": "The capability is historical, concrete, and closed.",
                }
            ],
            "summary": "Complete",
        },
    )
    write_json(
        root,
        CLEAN_FORWARD_LEDGER,
        {"paths": [], "status": "complete"},
    )
    write_text(
        root,
        CORRECTION,
        "# Correction\n\n"
        "Placeholder detection is context-aware.\n\n"
        "The historical quoted identifier `PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT` "
        "is evidence, not an unresolved value.\n\n"
        "Words such as placeholderization and placeholders remain ordinary prose.\n",
    )
    write_json(
        root,
        RECONCILIATION,
        {
            "non_supported_non_deferred_capabilities": [
                {"CapabilityKey": HISTORICAL_IDENTIFIER}
            ],
            "capabilities": [{"CapabilityKey": HISTORICAL_IDENTIFIER}],
            "status": "complete",
        },
    )
    write_json(
        root,
        TEST_SURFACE_ACCOUNTING,
        {"changes": [], "status": "complete"},
    )
    write_json(
        root,
        SEMGREP_TARGET_ACCOUNTING,
        {"targets": [], "status": "complete"},
    )


class PlaceholderGuardTest(unittest.TestCase):
    maxDiff = None

    def test_proven_false_positive_is_rejected_by_legacy_broad_behavior(self) -> None:
        legacy_broad_pattern = re.compile(
            r"TODO|TBD|FIXME|PLACEHOLDER|XXX|<fill-me>|\*\*\*",
            re.IGNORECASE,
        )
        self.assertIsNotNone(legacy_broad_pattern.search(HISTORICAL_IDENTIFIER))

    def test_proven_false_positive_is_accepted_structurally(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            result = run_guard(root)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "PLACEHOLDER_GATE=PASS\n")
        self.assertEqual(result.stderr, "")

    def test_reconciliation_historical_identifier_exact_paths_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            result = run_guard(root)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "PLACEHOLDER_GATE=PASS\n")
        self.assertEqual(result.stderr, "")

    def test_json_negative_control_matrix(self) -> None:
        for name, unresolved_value in JSON_NEGATIVE_CONTROLS:
            with self.subTest(name=name, value=unresolved_value):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    create_clean_fixture(root)
                    write_json(
                        root,
                        CLEAN_FORWARD_LEDGER,
                        {"nested": [{"required_value": unresolved_value}]},
                    )
                    result = run_guard(root)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(str(CLEAN_FORWARD_LEDGER), result.stdout)
                self.assertIn("$.nested[0].required_value", result.stdout)

    def test_markdown_negative_control_matrix(self) -> None:
        for name, markdown in MARKDOWN_NEGATIVE_CONTROLS:
            with self.subTest(name=name, markdown=markdown):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    create_clean_fixture(root)
                    write_text(root, CORRECTION, "# Correction\n\n" + markdown)
                    result = run_guard(root)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"{CORRECTION}:line 3", result.stdout)

    def test_same_capability_key_value_fails_in_other_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                CLEAN_FORWARD_LEDGER,
                {"capabilities": [{"CapabilityKey": HISTORICAL_IDENTIFIER}]},
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(str(CLEAN_FORWARD_LEDGER), result.stdout)
        self.assertIn("$.capabilities[0].CapabilityKey", result.stdout)

    def test_same_capability_key_value_fails_at_other_path_in_capability_ledger(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                CAPABILITY_LEDGER,
                {"archive": [{"CapabilityKey": HISTORICAL_IDENTIFIER}]},
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(str(CAPABILITY_LEDGER), result.stdout)
        self.assertIn("$.archive[0].CapabilityKey", result.stdout)

    def test_reconciliation_historical_identifier_fails_at_other_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                RECONCILIATION,
                {"archive": [{"CapabilityKey": HISTORICAL_IDENTIFIER}]},
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(str(RECONCILIATION), result.stdout)
        self.assertIn("$.archive[0].CapabilityKey", result.stdout)

    def test_reconciliation_historical_identifier_fails_in_another_artifact(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                TEST_SURFACE_ACCOUNTING,
                {"capabilities": [{"CapabilityKey": HISTORICAL_IDENTIFIER}]},
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(str(TEST_SURFACE_ACCOUNTING), result.stdout)
        self.assertIn("$.capabilities[0].CapabilityKey", result.stdout)

    def test_reconciliation_exception_rejects_another_structured_value(self) -> None:
        for collection in (
            "non_supported_non_deferred_capabilities",
            "capabilities",
        ):
            with self.subTest(collection=collection):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    create_clean_fixture(root)
                    write_json(
                        root,
                        RECONCILIATION,
                        {
                            collection: [
                                {"CapabilityKey": "PLACEHOLDER_ANOTHER_VALUE"}
                            ]
                        },
                    )
                    result = run_guard(root)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(str(RECONCILIATION), result.stdout)
                self.assertIn(
                    f"$.{collection}[0].CapabilityKey",
                    result.stdout,
                )

    def test_capability_exception_does_not_skip_other_values_in_same_row(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                CAPABILITY_LEDGER,
                {
                    "capabilities": [
                        {
                            "CapabilityKey": HISTORICAL_IDENTIFIER,
                            "owner": "TODO",
                        }
                    ]
                },
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("$.capabilities[0].owner", result.stdout)

    def test_capability_exception_does_not_exempt_unresolved_capability_key(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                CAPABILITY_LEDGER,
                {"capabilities": [{"CapabilityKey": "TODO"}]},
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("$.capabilities[0].CapabilityKey", result.stdout)

    def test_malformed_json_fails_closed_with_artifact_and_line(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_text(root, CLEAN_FORWARD_LEDGER, '{"status": "complete",\n')
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"{CLEAN_FORWARD_LEDGER}:line 2", result.stdout)
        self.assertIn("malformed JSON", result.stdout)

    def test_missing_artifact_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            (root / CORRECTION).unlink()
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"{CORRECTION}:<read>", result.stdout)
        self.assertIn("unable to read", result.stdout)

    def test_unresolved_value_in_each_new_artifact_fails(self) -> None:
        for artifact in NEW_JSON_TARGETS:
            for name, unresolved_value in NEW_ARTIFACT_UNRESOLVED_VALUES:
                with self.subTest(
                    artifact=artifact,
                    name=name,
                    value=unresolved_value,
                ):
                    with tempfile.TemporaryDirectory() as temporary_directory:
                        root = Path(temporary_directory)
                        create_clean_fixture(root)
                        write_json(
                            root,
                            artifact,
                            {"required_value": unresolved_value},
                        )
                        result = run_guard(root)

                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn(str(artifact), result.stdout)
                    self.assertIn("$.required_value", result.stdout)

    def test_each_missing_new_artifact_fails_closed(self) -> None:
        for artifact in NEW_JSON_TARGETS:
            with self.subTest(artifact=artifact):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    create_clean_fixture(root)
                    (root / artifact).unlink()
                    result = run_guard(root)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"{artifact}:<read>", result.stdout)
                self.assertIn("unable to read", result.stdout)

    def test_each_malformed_new_artifact_fails_closed(self) -> None:
        for artifact in NEW_JSON_TARGETS:
            with self.subTest(artifact=artifact):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory)
                    create_clean_fixture(root)
                    write_text(root, artifact, '{"status": "complete",\n')
                    result = run_guard(root)

                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"{artifact}:line 2", result.stdout)
                self.assertIn("malformed JSON", result.stdout)

    def test_json_reports_every_finding_in_deterministic_path_order(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_json(
                root,
                CLEAN_FORWARD_LEDGER,
                {"first": "TODO", "second": ["TBD"]},
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        first = result.stdout.index("$.first")
        second = result.stdout.index("$.second[0]")
        self.assertLess(first, second)

    def test_markdown_reports_exact_line_numbers_for_every_finding(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_text(
                root,
                CORRECTION,
                "# Correction\n\nstatus: TODO\n\n| owner | TBD |\n",
            )
            result = run_guard(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"{CORRECTION}:line 3", result.stdout)
        self.assertIn(f"{CORRECTION}:line 5", result.stdout)

    def test_clean_context_examples_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            create_clean_fixture(root)
            write_text(
                root,
                CORRECTION,
                "# Context-aware placeholder detection\n\n"
                "This placeholder detection is context-aware.\n"
                "The placeholderization pass preserves ordinary words containing "
                "placeholder.\n"
                "Historical ID: `PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT` is quoted.\n",
            )
            result = run_guard(root)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "PLACEHOLDER_GATE=PASS\n")

    def test_real_artifacts_pass_from_temporary_root_copy(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            for relative_path in TARGETS:
                destination = root / relative_path
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative_path, destination)
            result = run_guard(root)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "PLACEHOLDER_GATE=PASS\n")
        self.assertEqual(result.stderr, "")


def main() -> int:
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(PlaceholderGuardTest)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print(f"PLACEHOLDER_TEST_COUNT={result.testsRun}")
    print(f"PLACEHOLDER_NEGATIVE_CONTROL_COUNT={NEGATIVE_CONTROL_COUNT}")
    if result.wasSuccessful():
        print("PLACEHOLDER_NEGATIVE_CONTROL=PASS")
        return 0
    print("PLACEHOLDER_NEGATIVE_CONTROL=FAIL")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
