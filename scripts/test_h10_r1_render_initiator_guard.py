#!/usr/bin/env python3
"""Mutation controls for the H10-R1 Render initiator architecture guard."""

from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/h10-r1-render-initiator-guard.py"


class H10R1RenderInitiatorGuardMutationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.write(
            "render-module/src/main/java/example/RenderSubmission.java",
            "package example; final class RenderSubmission {}\n",
        )
        self.write("render-module/build.gradle.kts", "dependencies {}\n")
        self.write(
            "identity-access-module/src/main/java/example/Identity.java",
            "package example; final class Identity {}\n",
        )
        self.write(
            "delivery-module/src/main/java/example/DeliveryCompletion.java",
            "package example; final class DeliveryCompletion {}\n",
        )
        self.write(
            "delivery-module/src/main/java/com/example/platform/delivery/app/DeliveryJobService.java",
            """package example;
final class DeliveryJobService {
    int finalizeDeliveriesForRenderJob(String renderJobId) {
        var condition = DELIVERY_JOB.STATUS.eq(DeliveryJobStatus.QUEUED.name());
        return 0;
    }
}
""",
        )
        self.write(
            "outbox-event-module/src/main/java/example/Outbox.java",
            "package example; final class Outbox {}\n",
        )
        self.write(
            "platform-app/src/main/java/example/PlatformApp.java",
            "package example; final class PlatformApp {}\n",
        )
        self.write(
            "shared-kernel/src/main/java/example/Shared.java",
            "package example; final class Shared {}\n",
        )
        self.write(
            "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
            """create table render_job (
    id varchar(64) primary key,
    initiator_type varchar(32) not null,
    initiator_id varchar(128) not null,
    initiator_tenant_id varchar(64) not null
);
""",
        )
        self.write_event("RenderJobCompletedEvent")
        self.write_event("RenderJobFailedEvent")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def write_event(self, name: str, field: str = "RenderInitiator initiator") -> None:
        self.write(
            f"shared-kernel/src/main/java/com/example/platform/shared/events/{name}.java",
            f"package com.example.platform.shared.events; public record {name}({field}) {{}}\n",
        )

    def run_guard(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(GUARD), "--root", str(self.root)],
            text=True,
            capture_output=True,
            check=False,
        )

    def assert_red(self, metric: str) -> None:
        result = self.run_guard()
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertRegex(result.stdout, rf"(?m)^{metric}=[1-9][0-9]*$")
        self.assertIn("H10_R1_RENDER_INITIATOR_GUARD=FAIL", result.stdout)

    def test_green_control(self) -> None:
        result = self.run_guard()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("RENDER_INITIATOR_SCHEMA_COLUMN_COUNT=3", result.stdout)
        self.assertIn("H10_R1_RENDER_INITIATOR_GUARD=PASS", result.stdout)

    def test_rejects_render_notification_dependency(self) -> None:
        self.write("render-module/build.gradle.kts", 'implementation(project(":notification-module"))\n')
        self.assert_red("RENDER_TO_NOTIFICATION_PRODUCTION_DEPENDENCY_COUNT")

    def test_rejects_render_novu_reference(self) -> None:
        self.write("render-module/src/main/java/example/RenderSubmission.java", "class NovuRenderClient {}\n")
        self.assert_red("RENDER_NOVU_REFERENCE_COUNT")

    def test_rejects_identity_novu_reference(self) -> None:
        self.write("identity-access-module/src/main/java/example/Identity.java", "class NovuIdentityClient {}\n")
        self.assert_red("IDENTITY_NOVU_REFERENCE_COUNT")

    def test_rejects_project_as_recipient(self) -> None:
        self.write(
            "render-module/src/main/java/example/RenderSubmission.java",
            "void notifyProject() { NotificationAudience.recipient(projectId); }\n",
        )
        self.assert_red("PROJECT_ID_AS_NOTIFICATION_AUDIENCE_COUNT")

    def test_rejects_tenant_as_subscriber(self) -> None:
        self.write(
            "render-module/src/main/java/example/RenderSubmission.java",
            "void notifyTenant() { NotificationAudience.subscriber(tenantId); }\n",
        )
        self.assert_red("TENANT_ID_AS_NOTIFICATION_AUDIENCE_COUNT")

    def test_rejects_arbitrary_tenant_user_fallback(self) -> None:
        self.write("render-module/src/main/java/example/RenderSubmission.java", "var user = tenantMembers.findFirst();\n")
        self.assert_red("ARBITRARY_TENANT_USER_FALLBACK_COUNT")

    def test_rejects_duplicate_principal_identity_authority(self) -> None:
        self.write("render-module/src/main/java/example/PrincipalId.java", "record PrincipalId(String value) {}\n")
        self.assert_red("DUPLICATE_PRINCIPAL_ID_AUTHORITY_COUNT")

    def test_rejects_fake_human_system_principal(self) -> None:
        self.write("render-module/src/main/java/example/RenderSubmission.java", 'var actor = CanonicalActor.user("system@example.com", tenantId, roles, "fake");\n')
        self.assert_red("SYSTEM_RENDER_FAKE_PRINCIPAL_COUNT")

    def test_rejects_fake_system_fallback(self) -> None:
        self.write(
            "render-module/src/main/java/example/RenderSubmission.java",
            'var actor = RenderInitiator.restore(ActorType.SYSTEM, "system:missing", tenantId);\n',
        )
        self.assert_red("SYSTEM_RENDER_FAKE_PRINCIPAL_COUNT")

    def test_rejects_completion_time_ambient_actor(self) -> None:
        self.write(
            "render-module/src/main/java/example/RenderCompletion.java",
            "class RenderCompletion { CanonicalActorResolver resolver; void done() { resolver.resolveCurrentActor(); new RenderJobCompletedEvent(); } }\n",
        )
        self.assert_red("CURRENT_AMBIENT_ACTOR_AT_COMPLETION_COUNT")

    def test_rejects_delivery_render_initiator_table_read(self) -> None:
        self.write(
            "delivery-module/src/main/java/example/DeliveryCompletion.java",
            "class DeliveryCompletion { Object actor = RENDER_JOB.INITIATOR_ID; }\n",
        )
        self.assert_red("DELIVERY_RENDER_INITIATOR_RAW_TABLE_READ_COUNT")

    def test_rejects_failed_delivery_selection_in_finalizer(self) -> None:
        finalizer = self.root / (
            "delivery-module/src/main/java/com/example/platform/delivery/app/DeliveryJobService.java"
        )
        finalizer.write_text(
            finalizer.read_text(encoding="utf-8").replace(
                "DeliveryJobStatus.QUEUED.name())",
                "DeliveryJobStatus.QUEUED.name())\n"
                "                .or(DELIVERY_JOB.STATUS.eq(DeliveryJobStatus.FAILED.name()))",
            ),
            encoding="utf-8",
        )
        self.assert_red("FINALIZE_FAILED_DELIVERY_AUTO_RETRY_COUNT")

    def test_rejects_failure_time_security_context_access(self) -> None:
        self.write(
            "render-module/src/main/java/example/RenderFailure.java",
            "class RenderFailure { void fail() { SecurityContextHolder.getContext(); new RenderJobFailedEvent(); } }\n",
        )
        self.assert_red("CURRENT_AMBIENT_ACTOR_AT_FAILURE_COUNT")

    def test_rejects_missing_initiator_at_submission(self) -> None:
        self.write(
            "render-module/src/main/java/example/RenderSubmission.java",
            "class RenderSubmission { void submit() { orchestrator.submitRenderJob(request, null); } }\n",
        )
        self.assert_red("MISSING_INITIATOR_AT_SUBMISSION_COUNT")

    def test_rejects_new_render_initiator_schema_field(self) -> None:
        schema = self.root / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
        schema.write_text(
            schema.read_text().replace(
                "    initiator_tenant_id varchar(64) not null\n",
                "    initiator_tenant_id varchar(64) not null,\n    initiator_email varchar(255)\n",
            ),
            encoding="utf-8",
        )
        self.assert_red("NEW_SCHEMA_CHANGE_BEYOND_EXISTING_H10_R1_INITIATOR_COLUMNS")

    def test_rejects_missing_initiator_schema_column(self) -> None:
        schema = self.root / "platform-app/src/main/resources/db/migration/V1__initial_schema.sql"
        schema.write_text(schema.read_text().replace("    initiator_id varchar(128) not null,\n", ""), encoding="utf-8")
        result = self.run_guard()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("RENDER_INITIATOR_SCHEMA_COLUMN_COUNT=2", result.stdout)

    def test_rejects_missing_completed_event_initiator(self) -> None:
        self.write_event("RenderJobCompletedEvent", "String renderJobId")
        result = self.run_guard()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("RENDER_COMPLETED_EVENT_INITIATOR_FIELD_COUNT=0", result.stdout)

    def test_rejects_missing_failed_event_initiator(self) -> None:
        self.write_event("RenderJobFailedEvent", "String renderJobId")
        result = self.run_guard()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("RENDER_FAILED_EVENT_INITIATOR_FIELD_COUNT=0", result.stdout)

    def test_excludes_generated_build_worktree_and_node_modules(self) -> None:
        for relative in (
            "render-module/build/generated/Bad.java",
            "render-module/generated/Bad.java",
            ".worktrees/other/render-module/src/main/java/Bad.java",
            "render-module/node_modules/Bad.java",
            ".git/objects/Bad.java",
        ):
            self.write(relative, "class NovuNotificationEventPublisher {}\n")
        result = self.run_guard()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_fails_on_empty_scan_universe(self) -> None:
        with tempfile.TemporaryDirectory() as empty:
            result = subprocess.run(
                [sys.executable, str(GUARD), "--root", empty],
                text=True,
                capture_output=True,
                check=False,
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ERROR=EMPTY_SCAN_UNIVERSE", result.stdout)


if __name__ == "__main__":
    unittest.main()
