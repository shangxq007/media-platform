#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode = True

GUARD_PATH = Path(__file__).with_name(
    "verify-billing-entitlement-payment-authority-convergence-v1.py"
)
SPEC = importlib.util.spec_from_file_location("h5_i9_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class ImplementationGuardMutationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.files = guard.load_production_sources(guard.REPO)
        errors, _ = guard.validate_implementation(cls.files)
        if errors:
            raise AssertionError("repository fixture must be guard-clean: " + "; ".join(errors))

    def assert_mutation_rejected(self, path: str, source: str, code: str) -> None:
        mutated = dict(self.files)
        mutated[path] = source
        errors, _ = guard.validate_implementation(mutated)
        self.assertTrue(any(code in error for error in errors), errors)

    def test_added_quota_writer_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "render-module/src/main/java/example/InjectedQuotaWriter.java",
            "class InjectedQuotaWriter { void write() { dsl.insertInto(QUOTA_USAGE); } }",
            "WRITER_quota_usage",
        )

    def test_canonical_money_float_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "billing-module/src/main/java/com/example/platform/billing/domain/InjectedMoney.java",
            "record InjectedMoney(double priceAmount) {}",
            "CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT",
        )

    def test_render_commercial_shadow_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "render-module/src/main/java/com/example/platform/render/infrastructure/billing/decision/BillingDecisionEngine.java",
            "class BillingDecisionEngine {}",
            "RENDER_COMMERCIAL_SHADOW_COUNT",
        )

    def test_plan_specific_capability_fork_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "entitlement-module/src/main/java/com/example/platform/entitlement/app/InjectedCapabilityPolicy.java",
            'class InjectedCapabilityPolicy { boolean capability(String tier) { return "PRO".equals(tier); } }',
            "PLAN_SPECIFIC_CAPABILITY_BRANCH_COUNT",
        )

    def test_effective_capability_persistence_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "platform-app/src/main/java/com/example/platform/capability/effective/InjectedStore.java",
            "class InjectedStore { JdbcTemplate jdbcTemplate; void save() {} }",
            "EFFECTIVE_CAPABILITY_PERSISTENCE_HITS",
        )

    def test_raw_webhook_payload_persistence_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "payment-module/src/main/java/com/example/platform/payment/infrastructure/InjectedWebhookWriter.java",
            'class InjectedWebhookWriter { String rawPayload; String sql = "INSERT INTO provider_webhook_receipt(raw_payload)"; }',
            "RAW_PAYMENT_WEBHOOK_PAYLOAD_PERSISTENCE_HITS",
        )

    def test_observed_runtime_usage_mutation_is_rejected(self) -> None:
        self.assert_mutation_rejected(
            "billing-module/src/main/java/com/example/platform/usage/infrastructure/InjectedObservationMutation.java",
            'class InjectedObservationMutation { String sql = "UPDATE observed_runtime_usage SET quantity = 0"; }',
            "OBSERVED_RUNTIME_USAGE_MUTATION_WRITER_COUNT",
        )

    def test_missing_or_stale_accepted_ancestor_is_rejected(self) -> None:
        self.assertTrue(any("ACCEPTED_ANCESTOR_MISSING" in error
                            for error in guard.validate_accepted_ancestor(False, False)))
        self.assertTrue(any("ACCEPTED_ANCESTOR_NOT_IN_HISTORY" in error
                            for error in guard.validate_accepted_ancestor(True, False)))


if __name__ == "__main__":
    unittest.main()
