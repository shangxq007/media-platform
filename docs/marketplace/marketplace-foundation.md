# Marketplace owner contracts

`marketplace-module` owns persisted listings, publication decisions, and Marketplace reviews/threads/comments. Platform application composition explicitly imports its configuration. Render no longer owns Marketplace repositories, preparation handlers, or an in-memory item registry. Timeline reviews remain Timeline-only.

The currently evidenced publication subject is `MEDIA_ASSET`, using Media's `MediaAssetId` and exact `mediaVersion`. Other generic item kinds (plugins, effects, workflow definitions, arbitrary Artifact IDs) have no adopted direct publication command in this slice and reject explicitly. A listing reference is not a Media/Artifact/Storage access grant. Marketplace never commits physical output or provides fabricated preview/download URLs.

## Commands and authorization

All management commands require authenticated server identity and current Identity permissions on the actual Project/Workspace. `READ` is required for private reads, `marketplace.manage` for draft/edit/submit, `marketplace.review` for decisions/comments/resolution, and `marketplace.publish` for publication/withdrawal. The Media owner additionally authorizes its own publication-metadata update. Permission definitions do not grant roles automatically.

Workspace is resolved from Project through Identity, never from the current browser selection, tenant ID, Project ID, or an unverified body field. Actor/Account attribution is server-derived; body overrides are rejected. Known restricted classification, elevated security level or PII exclude a Media subject from publication and public discovery. Marketplace does not invent a license, entitlement, pricing or settlement grant; the existing Media governance facts and owning authorization contracts remain authoritative.

Create an admitted draft:

```http
POST /api/projects/{projectId}/marketplace/listings
Content-Type: application/json
```

```json
{
  "commandId": "caller-unique-command",
  "subject": {"kind": "MEDIA_ASSET", "assetId": {"value": "existing-media-id"}, "version": "v1"},
  "title": "Public title",
  "summary": "Public summary",
  "description": "Public description"
}
```

Responses contain the listing `id`, typed subject, actual scope, state and numeric aggregate `version`. Subsequent commands supply a new `commandId` and `expectedVersion`. Identical command retries by the same actor return the recorded result without repeating state/effects/facts; conflicting reuse is rejected. A stale version is rejected. Command identity is tenant-scoped, with actor/resource/input equality checked.

| Route under `/api/projects/{projectId}/marketplace` | Purpose |
|---|---|
| `GET /listings` and `GET /listings/{id}` | Authorized private listing projections |
| `PATCH /listings/{id}` | Edit a DRAFT; title/summary/description, clears its current review |
| `POST /listings/{id}/reviews` | Submit the exact pinned subject; title/private description |
| `GET /reviews/{id}` | Authorized private review and comments |
| `POST /reviews/{id}/decisions` | `decision`: APPROVE, REQUEST_CHANGES or REJECT |
| `POST /reviews/{id}/comments` | Add `content`; optional existing `threadId` must belong to that open review |
| `POST /reviews/{id}/resolve` | Resolve the exact `threadId` |
| `POST /listings/{id}/transitions` | `transition`: PUBLISH or ARCHIVE |

DRAFT → submitted review → approved READY → PUBLISHED → ARCHIVED. Archive can withdraw an earlier state; ARCHIVED is terminal. Editing requires DRAFT and forces a new review. Comments reopen the review/draft and unresolved threads block approval/publication. Distinct reviewers retain distinct decision facts; a duplicated actor decision uses the original command identity. Old reviews cannot affect a newly edited/resubmitted listing. Publication rechecks the exact current Media version/eligibility. Withdrawal can hide stale/restricted metadata without mutating a foreign or changed Media version.

Existing asset-facing submit/decision/publish routes delegate to the same owner and now require command/version bodies. They no longer accept author/reviewer overrides or manufacture a listing/review implicitly. The old unscoped listing-status PATCH is retired; READY cannot be assigned to bypass review. Productization's `POST /api/product/marketplace/{workspaceId}/items` accepts `{projectId, listing: <create-command>}` and verifies that actual Workspace before admission. Its search uses public published metadata. Phantom category/rating/popularity operations from the in-memory registry are retired.

## Public discovery

`GET /api/marketplace/search`, `/listings`, `/listings/{id}` and `/discovery` are public curated-metadata reads. Only admitted PUBLISHED listings with a still-valid eligible subject and actual scope are returned. Private/draft status and private tenant/project filter overrides are rejected; use the authorized project management API. Private review content, reviewer/Account data, storage keys and access URLs are not public projections. Popular/featured feeds are empty until a real ranking/curation contract exists; no ratings are invented.

`GET /api/marketplace/assets/{assetId}/listing` is an authenticated private management read, not a public asset lookup. Dashboard and workbench callers consume owner projections; no Render Marketplace repository is exposed.

## Facts, transactions and recovery

Listing/review mutation, Media publication metadata where applicable, command receipt and required fact append share the application transaction. Marketplace commands lock the listing and validate expected version. Media's scoped snapshot holds a shared row lock until projection/command completion. Search reindex reads current owner facts under that lock, so an older event cannot write a stale publication state over a newer projection. Events never decide Marketplace state.

The existing common Outbox registers schema version 1 for:

- `marketplace.listing.created`, `.updated`, `.published`, `.archived`.
- `marketplace.review.created`, `.approved`, `.changes_requested`, `.rejected`, `.comment.added`, `.thread.resolved`.

Each typed record carries a fact identity, listing identity/version, exact typed subject, Identity ProjectScope, accepted actor/Account/type and timestamp. Review/decision/comment/thread identities are explicit. Audit and Notification retain the existing approval/publication/archive consumers, now using domain fact keys for deduplication and authoritative attribution. Search consumes publication/archive facts and creates one existing coordination intent per fact. Other contracts are intentional registered publication facts with real owner producers; no fictional consumer is claimed. Timeline's accepted typed events remain unchanged.

Consumer retry is common Outbox retry/claim recovery. Audit/Notification/coordination use their existing durable deduplication facilities. Tests use participating PostgreSQL effects and stable notification command IDs; this is not exactly-once external provider execution. No second event bus or scheduler is added.

## Forward migrations and historical data

V1–V5 are unchanged. V6 adds Marketplace admission/version/Workspace fields, preserves existing listing data in `legacy_snapshot`, and adds separate review/decision/thread/comment and command-receipt tables. Legacy listings are not publicly executable merely because their old status says PUBLISHED. An authorized explicit create/admission for the actual Media subject can readmit a coherent legacy row as DRAFT while retaining its snapshot; old approvals are not reused. Ambiguous foreign scope requires owner reconciliation.

Historical `timeline_review` ASSET rows and their child records remain unchanged evidence, but are excluded from live Timeline APIs; no ASSET bridge survives. V6 marks pending retired preparation tasks/jobs and exact old publication reindex intents missing scope FAILED, preserving payloads and task reasons. The old coordination discriminator remains readable history, with no production producer/handler.

V7 places pending/failed/processing `asset.submitted.review`, `asset.approved`, `asset.published`, `asset.archived` Outbox rows into the existing DEAD_LETTER state with `RETIRED_MARKETPLACE_EVENT_SCHEMA`. Their bytes, versions and keys remain available for operator reconciliation. Processed history is untouched. Missing actor/listing/version evidence is never guessed; retired schemas are unregistered, so a manual replay cannot activate a compatibility handler. V7 also fences unversioned late writers: admitted subject/scope/creator are immutable, and every update must advance the listing version once.

Deployment must drain old writers and inspect preserved failed/dead-letter work; this batch performs no production migration or deployment. No applied migration checksum is repaired.

## Focused verification

Use a disposable Docker-compatible PostgreSQL endpoint and `bash scripts/test-authority-modules.sh marketplace`. Owner module contract tests, real Spring/HTTP/PostgreSQL command and consumer cases, migration controls, relevant Identity/Media/Timeline/Render/Audit/Notification/Outbox tests and architecture gates are recorded in the batch handoff. No new marketplace frontend, plugin commerce, real external publishing, settlement or deployment is claimed.

Archive uses a Media-owned atomic tenant/Project/asset/version predicate. A conditional miss withdraws only listing metadata; authorization or database errors roll back the entire command. The current Media object is never adopted as a replacement for the listing pin.
