# Audit ports and Storage URI references (EP27B)

Audit owns `audit-contract-module`, published as `com.example.platform.auditcontract.api`.
Its independent logical module `audit-ports :: api` avoids a dependency cycle between
Audit ingestion and domains that both supply Audit inputs and consume Audit ports.
The artifact adds no persistence implementation or registry. AuditPortAdapter and
AdminAuditPublisherImpl in audit-compliance-module remain the sole runtime bindings.

AuditPort is required: persistence failures propagate to the caller. In a caller's
Spring database transaction its write participates in that transaction. AdminAuditPublisher
retains its explicitly best-effort logging and sanitization contract; this migration
adds no guarantee of mandatory persistence to administrative diagnostics.
Persisted actor identity comes from the server observation principal. Without a
principal the existing `system` unattributed sentinel is used; a tenant ID is never
substituted for an actor. Caller payload and administrative logging hints do not
replace that persisted actor. Neither port is an authorization service.

Storage owns StorageUriReferenceContributor and StorageUriReferenceHit in its existing
`storage.contract` surface. Render uses those contracts for physical URI deletion
checks; Delivery contributes references from its jobs and Artifact's accepted output
index. Artifact still owns accepted output identity and commitment. Relocation changes
no record fields, reference queries, storage URI meaning, event payload or schema.

The unused shared AssetDownloadUrlPort and conditional S3AssetDownloadUrlPort are
retired. They had no production consumer. Storage BlobStorage, S3 object writing,
materialization and supported presign interfaces remain. Project metadata export stays
supported; linked-asset export still fails explicitly pending its existing exact
Artifact-scope contract. Historical documents describing the unused adapter do not
constitute current runtime wiring or a new download obligation.

AuditContractBoundaryTest prevents old contracts/imports from returning;
AuditAssetOwnershipTest exercises actual Spring assembly with S3 enabled, PostgreSQL
failure/rollback/retry and authoritative attribution. The test uses no real S3 operation.
RenderOutputAcceptanceTest covers retained Storage/Artifact output and references.
No new transaction synchronization, retry policy, durable spool or schema is introduced.
