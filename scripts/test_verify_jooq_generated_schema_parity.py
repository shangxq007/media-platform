#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


sys.dont_write_bytecode = True
VERIFIER_PATH = Path(__file__).with_name("verify-jooq-generated-schema-parity.py")
SPEC = importlib.util.spec_from_file_location("jooq_schema_parity_verifier", VERIFIER_PATH)
assert SPEC is not None and SPEC.loader is not None
verifier = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = verifier
SPEC.loader.exec_module(verifier)


class IdentityParityNegativeControlTest(unittest.TestCase):
    def assert_rejected(
        self,
        canonical: list[str],
        tables: list[str],
        records: list[str],
        code: str,
    ) -> None:
        with self.assertRaisesRegex(verifier.VerificationError, code):
            verifier.verify_identity_parity(canonical, tables, records)

    def test_missing_generated_table(self) -> None:
        self.assert_rejected(["alpha", "beta"], ["alpha"], ["alpha", "beta"], "MISSING_GENERATED_TABLE")

    def test_unexpected_generated_table(self) -> None:
        self.assert_rejected(["alpha"], ["alpha", "beta"], ["alpha"], "UNEXPECTED_GENERATED_TABLE")

    def test_missing_generated_record(self) -> None:
        self.assert_rejected(["alpha", "beta"], ["alpha", "beta"], ["alpha"], "MISSING_GENERATED_RECORD")

    def test_unexpected_generated_record(self) -> None:
        self.assert_rejected(["alpha"], ["alpha"], ["alpha", "beta"], "UNEXPECTED_GENERATED_RECORD")

    def test_same_cardinality_mismatched_table_identities(self) -> None:
        self.assert_rejected(
            ["alpha", "beta"], ["alpha", "gamma"], ["alpha", "beta"], "MISSING_GENERATED_TABLE"
        )
        with self.assertRaisesRegex(verifier.VerificationError, "UNEXPECTED_GENERATED_TABLE"):
            verifier.verify_identity_parity(
                ["alpha", "beta"], ["alpha", "gamma"], ["alpha", "beta"]
            )

    def test_empty_expected_universe(self) -> None:
        self.assert_rejected([], ["alpha"], ["alpha"], "CANONICAL_TABLE_UNIVERSE_EMPTY")

    def test_empty_generated_table_universe(self) -> None:
        self.assert_rejected(["alpha"], [], ["alpha"], "GENERATED_TABLE_UNIVERSE_EMPTY")

    def test_empty_generated_record_universe(self) -> None:
        self.assert_rejected(["alpha"], ["alpha"], [], "GENERATED_RECORD_UNIVERSE_EMPTY")

    def test_duplicate_canonical_identity(self) -> None:
        self.assert_rejected(["alpha", "alpha"], ["alpha"], ["alpha"], "CANONICAL_TABLE_IDENTITY_DUPLICATE")

    def test_duplicate_generated_table_identity(self) -> None:
        self.assert_rejected(["alpha"], ["alpha", "alpha"], ["alpha"], "GENERATED_TABLE_IDENTITY_DUPLICATE")

    def test_duplicate_generated_record_identity(self) -> None:
        self.assert_rejected(["alpha"], ["alpha"], ["alpha", "alpha"], "GENERATED_RECORD_IDENTITY_DUPLICATE")


class CanonicalSqlParserTest(unittest.TestCase):
    def test_parses_actual_bounded_grammar_and_ignores_comments_and_literals(self) -> None:
        sql = """
            -- CREATE TABLE ignored_comment (id int);
            CREATE TABLE alpha (id integer, constraint nested check ((id > 0)));
            CREATE TABLE "user" (value text default 'CREATE TABLE ignored_literal (');
        """
        self.assertEqual(["alpha", "user"], verifier.parse_canonical_table_identities(sql))

    def test_malformed_create_table_fails_closed(self) -> None:
        with self.assertRaisesRegex(verifier.VerificationError, "CANONICAL_SQL_UNRECOGNIZED"):
            verifier.parse_canonical_table_identities("CREATE TABLE broken id integer);\n")

    def test_unrecognized_create_table_form_fails_closed(self) -> None:
        with self.assertRaisesRegex(verifier.VerificationError, "unrecognized CREATE .* TABLE"):
            verifier.parse_canonical_table_identities("CREATE TEMPORARY TABLE alpha (id integer);\n")

    def test_unterminated_create_table_fails_closed(self) -> None:
        with self.assertRaisesRegex(verifier.VerificationError, "not semicolon-terminated"):
            verifier.parse_canonical_table_identities("CREATE TABLE alpha (id integer)")

    def test_duplicate_canonical_declarations_fail_closed(self) -> None:
        with self.assertRaisesRegex(verifier.VerificationError, "CANONICAL_TABLE_IDENTITY_DUPLICATE"):
            verifier.parse_canonical_table_identities(
                "CREATE TABLE alpha (id integer); CREATE TABLE alpha (id integer);"
            )


class GeneratedJavaParserTest(unittest.TestCase):
    TABLE_SOURCE = """
        public class Alpha extends TableImpl<AlphaRecord> {
            public static final Alpha ALPHA = new Alpha();
            public Class<AlphaRecord> getRecordType() { return AlphaRecord.class; }
            public Alpha() { this(DSL.name("alpha"), null); }
        }
    """
    RECORD_SOURCE = """
        public class AlphaRecord extends UpdatableRecordImpl<AlphaRecord> {
            public AlphaRecord() { super(Alpha.ALPHA); }
        }
    """

    def test_table_and_record_identity_are_derived_through_binding(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            table_path = root / "Alpha.java"
            record_path = root / "AlphaRecord.java"
            table_path.write_text(self.TABLE_SOURCE, encoding="utf-8")
            record_path.write_text(self.RECORD_SOURCE, encoding="utf-8")
            table = verifier.parse_generated_table(table_path)
            record = verifier.parse_generated_record(
                record_path, {(table.class_name, table.singleton_name): table}
            )
            self.assertEqual("alpha", table.identity)
            self.assertEqual("alpha", record.identity)

    def test_unrecognized_table_constructor_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "Alpha.java"
            path.write_text(
                self.TABLE_SOURCE.replace('DSL.name("alpha")', "unknownNameSource()"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                verifier.VerificationError,
                "GENERATED_TABLE_IDENTITY_UNRECOGNIZED",
            ):
                verifier.parse_generated_table(path)

    def test_unrecognized_record_binding_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            table_path = root / "Alpha.java"
            record_path = root / "AlphaRecord.java"
            table_path.write_text(self.TABLE_SOURCE, encoding="utf-8")
            record_path.write_text(
                self.RECORD_SOURCE.replace("Alpha.ALPHA", "Unknown.UNKNOWN"),
                encoding="utf-8",
            )
            table = verifier.parse_generated_table(table_path)
            with self.assertRaisesRegex(
                verifier.VerificationError,
                "GENERATED_RECORD_UNEXPECTED_TABLE_BINDING",
            ):
                verifier.parse_generated_record(
                    record_path, {(table.class_name, table.singleton_name): table}
                )


class RepositoryIntegrationTest(unittest.TestCase):
    def test_current_canonical_and_generated_trees_have_exact_identity_parity(self) -> None:
        repo = Path(__file__).resolve().parents[1]
        canonical_count, table_count, record_count, java_file_count = verifier.verify(
            repo / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
            repo / "typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated",
        )
        self.assertGreater(canonical_count, 0)
        self.assertEqual(canonical_count, table_count)
        self.assertEqual(canonical_count, record_count)
        self.assertGreaterEqual(java_file_count, table_count + record_count)


if __name__ == "__main__":
    unittest.main()
