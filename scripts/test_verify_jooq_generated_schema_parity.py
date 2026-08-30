#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import shutil
import sys
import tempfile
from typing import Callable
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
    OWNER_KEYS = (
        (
            "UQ_STORAGE_LOGICAL_OBJECT_OWNER_IDEMPOTENCY",
            "StorageLogicalObject",
        ),
        (
            "UQ_STORAGE_WRITE_INTENT_OWNER_IDEMPOTENCY",
            "StorageWriteIntent",
        ),
    )

    def setUp(self) -> None:
        self.repo = Path(__file__).resolve().parents[1]
        self.schema = (
            self.repo
            / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
        )
        self.generated = (
            self.repo
            / "typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated"
        )

    def mutate_owner_key_fields(
        self,
        generated_root: Path,
        key_symbol: str,
        table_class: str,
        mutate: Callable[[list[str]], list[str]],
    ) -> None:
        keys_path = generated_root / "Keys.java"
        lines = keys_path.read_text(encoding="utf-8").splitlines(keepends=True)
        matching_indexes = [
            index for index, line in enumerate(lines) if f" {key_symbol} = " in line
        ]
        self.assertEqual(1, len(matching_indexes))
        line_index = matching_indexes[0]
        line = lines[line_index]
        fields_start = line.index("new TableField[] { ") + len("new TableField[] { ")
        fields_end = line.index(" }, true);", fields_start)
        fields = line[fields_start:fields_end].split(", ")
        expected_prefix = fields[0].rsplit(".", 1)[0] + "."
        self.assertTrue(expected_prefix.startswith(f"{table_class}."))
        self.assertTrue(all(field.startswith(expected_prefix) for field in fields))
        mutated_fields = mutate(fields)
        self.assertNotEqual(fields, mutated_fields)
        lines[line_index] = (
            line[:fields_start] + ", ".join(mutated_fields) + line[fields_end:]
        )
        keys_path.write_text("".join(lines), encoding="utf-8")

    def test_current_canonical_and_generated_trees_have_exact_identity_parity(self) -> None:
        canonical_count, table_count, record_count, java_file_count = verifier.verify(
            self.schema,
            self.generated,
        )
        self.assertGreater(canonical_count, 0)
        self.assertEqual(canonical_count, table_count)
        self.assertEqual(canonical_count, record_count)
        self.assertGreaterEqual(java_file_count, table_count + record_count)

    def test_each_owner_key_rejects_generated_project_id_removal(self) -> None:
        for key_symbol, table_class in self.OWNER_KEYS:
            with (
                self.subTest(key_symbol=key_symbol),
                tempfile.TemporaryDirectory() as temporary_directory,
            ):
                generated_root = Path(temporary_directory) / "generated"
                shutil.copytree(self.generated, generated_root)

                def drop_project_id(fields: list[str]) -> list[str]:
                    project_fields = [
                        field for field in fields if field.endswith(".PROJECT_ID")
                    ]
                    self.assertEqual(1, len(project_fields))
                    return [field for field in fields if field != project_fields[0]]

                self.mutate_owner_key_fields(
                    generated_root, key_symbol, table_class, drop_project_id
                )
                with self.assertRaisesRegex(
                    verifier.VerificationError,
                    "OWNER_UNIQUE_KEY_PARITY",
                ):
                    verifier.verify(self.schema, generated_root)

    def test_owner_key_rejects_generated_wrong_field_order(self) -> None:
        key_symbol, table_class = self.OWNER_KEYS[0]
        with tempfile.TemporaryDirectory() as temporary_directory:
            generated_root = Path(temporary_directory) / "generated"
            shutil.copytree(self.generated, generated_root)

            def swap_first_two_fields(fields: list[str]) -> list[str]:
                self.assertGreaterEqual(len(fields), 2)
                return [fields[1], fields[0], *fields[2:]]

            self.mutate_owner_key_fields(
                generated_root, key_symbol, table_class, swap_first_two_fields
            )
            with self.assertRaisesRegex(
                verifier.VerificationError,
                "OWNER_UNIQUE_KEY_PARITY",
            ):
                verifier.verify(self.schema, generated_root)

    def test_owner_key_rejects_schema_generated_disagreement(self) -> None:
        key_symbol, _ = self.OWNER_KEYS[0]
        constraint_name = key_symbol.casefold()
        schema_lines = self.schema.read_text(encoding="utf-8").splitlines(keepends=True)
        constraint_indexes = [
            index
            for index, line in enumerate(schema_lines)
            if constraint_name in line.casefold()
        ]
        self.assertEqual(1, len(constraint_indexes))
        columns_index = constraint_indexes[0] + 1
        self.assertIn("project_id, ", schema_lines[columns_index].casefold())
        schema_lines[columns_index] = schema_lines[columns_index].replace(
            "project_id, ", "", 1
        )

        with tempfile.TemporaryDirectory() as temporary_directory:
            schema_path = Path(temporary_directory) / "V1__initial_schema.sql"
            schema_path.write_text("".join(schema_lines), encoding="utf-8")
            with self.assertRaisesRegex(
                verifier.VerificationError,
                "OWNER_UNIQUE_KEY_PARITY_SCHEMA_GENERATED_DISAGREEMENT",
            ):
                verifier.verify(schema_path, self.generated)


if __name__ == "__main__":
    unittest.main()
