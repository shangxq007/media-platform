#!/usr/bin/env python3
"""Executable RED mutation matrix for the M0/M1 storage authority guard."""

from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("check-storage-identity-placement-m0-m1.py")


def write(root: Path, relative: str, content: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def baseline(root: Path) -> None:
    write(root, "storage-module/src/main/java/com/example/platform/storage/contract/StorageProvider.java", "interface StorageProvider {}\n")
    write(root, "storage-module/src/main/java/com/example/platform/storage/contract/provider/StorageProvider.java", "interface StorageProvider {}\n")
    write(root, "storage-module/src/main/java/com/example/platform/storage/api/StorageObjectIssuance.java", "// CANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY\n")
    write(root, "storage-module/src/main/java/com/example/platform/storage/api/StorageWriteIntentRecovery.java", "// STORAGE_WRITE_INTENT_RECOVERY_AUTHORITY\n")
    write(root, "storage-module/src/main/java/com/example/platform/storage/domain/identity/Allocator.java", "// CANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY\n")
    write(
        root,
        "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
        """create table storage_logical_object (
    object_id varchar(64), tenant_id varchar(64), project_id varchar(64),
    issuance_idempotency_key varchar(255),
    unique nulls not distinct (tenant_id, project_id, issuance_idempotency_key)
);
create table storage_object_placement (replica_id varchar(64));
create table storage_write_intent (
    write_intent_id varchar(64), tenant_id varchar(64), project_id varchar(64),
    issuance_idempotency_key varchar(255),
    unique nulls not distinct (tenant_id, project_id, issuance_idempotency_key)
);
""",
    )
    write(
        root,
        "storage-module/src/main/java/com/example/platform/storage/infrastructure/identity/JdbcStorageWriteIntentRepository.java",
        """class JdbcStorageWriteIntentRepository {
    void lockOwnerKey(Object owner, Object key) {
        String project = owner.projectId();
        advisoryLock("storage-write-intent-owner-key-v1|tenant:value:"
                + owner.tenantId().length() + ":" + owner.tenantId()
                + "|" + (project == null ? "project:null"
                        : "project:value:" + project.length() + ":" + project)
                + "|key:value:" + key.value().length() + ":" + key.value());
    }
    Object findByOwnerKey(Object owner, Object key) {
        return query("where tenant_id = ? and project_id is not distinct from ? and issuance_idempotency_key = ?",
                owner.tenantId(), owner.projectId(), key.value());
    }
}
""",
    )
    write(
        root,
        "storage-module/src/main/java/com/example/platform/storage/infrastructure/identity/JdbcStorageObjectAuthorityRepository.java",
        """class JdbcStorageObjectAuthorityRepository {
    Object findOriginalIssuance(Object owner, Object key) {
        return query("select r.replica_id, r.provider_id, r.placement_state, r.committed_digest "
                + "from storage_logical_object o join storage_placement_receipt r on r.object_id = o.object_id "
                + "where o.tenant_id = ? and o.project_id is not distinct from ? "
                + "and o.issuance_idempotency_key = ? and r.receipt_purpose = 'ORIGINAL_ISSUANCE'");
    }
}
""",
    )
    ledger = "id\tclassification\tstatus\nX1\tCURRENT_CANONICAL_FACT\tCLASSIFIED\n"
    for name in ("current-vs-target-fact-classification.tsv", "writer-inventory.tsv", "reader-inventory.tsv"):
        write(root, "docs/architecture/governance/storage-object-identity-placement-migration-v1/" + name, ledger)


def run(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(SCRIPT), "--root", str(root)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="storage-m0-m1-red-") as temp:
        base = Path(temp) / "base"
        baseline(base)
        green = run(base)
        if green.returncode != 0:
            print(green.stdout)
            return 1

        mutations = {
            "CANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/identity/SecondIssuance.java",
                "// CANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY\n"),
            "CANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/domain/identity/Second.java",
                "// CANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY\n"),
            "STORAGE_WRITE_INTENT_RECOVERY_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/identity/SecondRecovery.java",
                "// STORAGE_WRITE_INTENT_RECOVERY_AUTHORITY\n"),
            "RAW_STORAGE_PROVIDER_LOGICAL_ID_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/contract/StorageProvider.java",
                "interface StorageProvider { StorageObjectId allocateLogicalStorageObjectId(); }\n"),
            "TYPED_PROVIDER_LOGICAL_ID_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/contract/provider/StorageProvider.java",
                "interface StorageProvider { StorageObjectId issueStorageObjectId(); }\n"),
            "PROVIDER_BACKEND_LOGICAL_ID_AUTHORITY_COUNT": (
                "storage-provider-opendal/src/main/java/Backend.java",
                "class Backend { StorageObjectId mintStorageObjectId() { return null; } }\n"),
            "PHYSICAL_REFERENCE_TO_LOGICAL_ID_CONSTRUCTION_NEW_CODE_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/identity/Bad.java",
                "class Bad { Object x(Object placement) { return new StorageObjectId(placement.opaqueLocator()); } }\n"),
            "PLACEMENT_NAMESPACE_OWNER_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/identity/BadOwner.java",
                "class BadOwner { Object x(Object placement) { return new StorageOwnershipScope(placement.namespace().tenantId(), null); } }\n"),
            "GENERIC_JSON_PLACEMENT_AUTHORITY_COUNT": (
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
                "create table storage_object_placement (replica_id varchar(64), placement_json jsonb);\n"),
            "ARTIFACT_PHYSICAL_PLACEMENT_AUTHORITY_COUNT": (
                "artifact-module/src/main/java/BadArtifactAuthority.java",
                "// STORAGE_PHYSICAL_PLACEMENT_AUTHORITY\n"),
            "NON_STORAGE_STORAGE_INFRASTRUCTURE_REPOSITORY_IMPORT_COUNT": (
                "render-module/src/main/java/BadRepositoryImport.java",
                "import com.example.platform.storage.infrastructure.identity.JdbcStorageObjectAuthorityRepository;\n"),
            "DUAL_STORAGE_IDENTITY_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/identity/Dual.java",
                "// DUAL_STORAGE_IDENTITY_AUTHORITY\n"),
            "PERMANENT_STORAGE_FALLBACK_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/identity/Fallback.java",
                "// PERMANENT_STORAGE_FALLBACK\n"),
            "M2_PLUS_RUNTIME_ACTIVATION_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/migration/Adopt.java",
                "class Adopt { void adoptLegacyPlacement() {} }\n"),
            "CALLER_CANONICAL_BOOLEAN_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/migration/CallerTrust.java",
                "record CallerTrust(boolean canonicalRequested) {}\n"),
            "ENDPOINT_STABLE_IDENTITY_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/migration/EndpointIdentity.java",
                "class EndpointIdentity { Object x(Object endpoint) { return fingerprint(endpoint); } }\n"),
            "OBSERVATION_TIME_STABLE_IDENTITY_AUTHORITY_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/app/migration/TimeIdentity.java",
                "class TimeIdentity { Object x(Object observedAt) { return fingerprint(observedAt); } }\n"),
            "TENANT_ONLY_STORAGE_LOGICAL_OBJECT_UNIQUENESS_COUNT": (
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
                """create table storage_logical_object (
    object_id varchar(64), tenant_id varchar(64), project_id varchar(64),
    issuance_idempotency_key varchar(255),
    unique (tenant_id, issuance_idempotency_key)
);
create table storage_object_placement (replica_id varchar(64));
create table storage_write_intent (
    write_intent_id varchar(64), tenant_id varchar(64), project_id varchar(64),
    issuance_idempotency_key varchar(255),
    unique nulls not distinct (tenant_id, project_id, issuance_idempotency_key)
);
"""),
            "TENANT_ONLY_STORAGE_WRITE_INTENT_UNIQUENESS_COUNT": (
                "platform-app/src/main/resources/db/migration/V1__initial_schema.sql",
                """create table storage_logical_object (
    object_id varchar(64), tenant_id varchar(64), project_id varchar(64),
    issuance_idempotency_key varchar(255),
    unique nulls not distinct (tenant_id, project_id, issuance_idempotency_key)
);
create table storage_object_placement (replica_id varchar(64));
create table storage_write_intent (
    write_intent_id varchar(64), tenant_id varchar(64), project_id varchar(64),
    issuance_idempotency_key varchar(255),
    unique (tenant_id, issuance_idempotency_key)
);
"""),
            "OWNER_LOCK_PROJECT_DOMAIN_OMISSION_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/infrastructure/identity/JdbcStorageWriteIntentRepository.java",
                """class JdbcStorageWriteIntentRepository {
    void lockOwnerKey(Object owner, Object key) {
        advisoryLock(owner.tenantId() + "|" + key.value());
    }
    Object findByOwnerKey(Object owner, Object key) {
        return query("where tenant_id = ? and project_id is not distinct from ? and issuance_idempotency_key = ?",
                owner.tenantId(), owner.projectId(), key.value());
    }
}
"""),
            "OWNER_LOOKUP_PROJECT_PREDICATE_OMISSION_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/infrastructure/identity/JdbcStorageWriteIntentRepository.java",
                """class JdbcStorageWriteIntentRepository {
    void lockOwnerKey(Object owner, Object key) {
        String project = owner.projectId();
        advisoryLock("storage-write-intent-owner-key-v1|tenant:value:"
                + owner.tenantId().length() + ":" + owner.tenantId()
                + "|" + (project == null ? "project:null"
                        : "project:value:" + project.length() + ":" + project)
                + "|key:value:" + key.value().length() + ":" + key.value());
    }
    Object findByOwnerKey(Object owner, Object key) {
        return query("where tenant_id = ? and issuance_idempotency_key = ?",
                owner.tenantId(), key.value());
    }
}
"""),
            "ORIGINAL_REPLAY_MUTABLE_PLACEMENT_FACT_SELECTION_COUNT": (
                "storage-module/src/main/java/com/example/platform/storage/infrastructure/identity/JdbcStorageObjectAuthorityRepository.java",
                """class JdbcStorageObjectAuthorityRepository {
    Object findOriginalIssuance(Object owner, Object key) {
        return query("select p.replica_id, p.provider_id, p.placement_state, p.committed_digest "
                + "from storage_logical_object o join storage_placement_receipt r on r.object_id = o.object_id "
                + "join storage_object_placement p on p.object_id = r.object_id and p.replica_id = r.replica_id "
                + "where o.tenant_id = ? and o.project_id is not distinct from ? "
                + "and o.issuance_idempotency_key = ? and r.receipt_purpose = 'ORIGINAL_ISSUANCE'");
    }
}
"""),
            "UNCLASSIFIED": (
                "docs/architecture/governance/storage-object-identity-placement-migration-v1/writer-inventory.tsv",
                "id\tclassification\tstatus\nX1\t\tUNCLASSIFIED\n"),
        }

        failed = False
        for counter, (relative, content) in mutations.items():
            case = Path(temp) / counter.lower()
            shutil.copytree(base, case)
            write(case, relative, content)
            result = run(case)
            if result.returncode == 0 or f"ERROR {counter}:" not in result.stdout:
                print(f"RED_CONTROL {counter}=FAIL")
                print(result.stdout)
                failed = True
            else:
                print(f"RED_CONTROL {counter}=PASS")
        return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
