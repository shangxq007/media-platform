# Revision history and review browsing

Entry: Workspace Projects → **Browse revision history and reviews**, `/w/$workspaceId/history`.
This is a read-only browser, separate from unresolved Project Open/editor context. It offers explicitly selected authorized projects, up to 30 canonical revisions and 30 reviews, revision summaries, authoritative head indication, returned-review text/status filters, and review detail (comments, threads, decisions, merge guard). It has no mutation or snapshot/JSON viewer.

## Accepted contract

Base: `7b1ab20849eb26433b1147103599b43bdeb83d69`.
`platformClient.workspace.getHome` revalidates Workspace/tenant context. Identity `GET /api/identity/tenants/{tenantId}/projects` resolves the actual actor, checks tenant equality and filters each project with READ authorization. `GET /api/identity/projects/{projectId}` revalidates the selected project. The existing authenticated discovery adapter from Render is reused; Dashboard recency never grants project selection.

`TimelineRevisionController` uses `/api/render/projects/{projectId}/timeline/revisions?limit=30`, optional server filters `editSessionId`, `authorUserId`, `source` (not used here), and `/head`. There is no pagination/total contract. Head 404 means no head; project existence/access is separately checked. List fields are strictly decoded, including `isMerge`, nullable merge parents/base, labels, author, source, message, timestamp, patch count, and change summary. List position never implies head. No snapshot request occurs.

`TimelineReviewController` exposes `/api/render/projects/{projectId}/timeline/reviews?limit=30` and `/{reviewId}`. Its detail fields are review/comments/threads/decisions/mergeGuard; each child is checked against the requested review ID. Review status and decision strings remain backend values, with labels for known values and verbatim display for unknown values. Merge reasons are shown unchanged. Associated revision IDs are always shown; revision numbers/messages are used only when present in the returned revision set. A merge guard is advisory, not a permission or editor bootstrap.

Every Timeline read first uses `TimelineProjectAuthorizationService.requireRead`; persisted review lookup is scoped by project and ambient tenant. Shared Axios HTTP errors are preserved. Browsing memory is binding/tenant/project scoped and contains only preferences, never response data. Returning to the route revalidates Workspace, discovery and project reads before restoring preferences. Project/workspace/session changes abort and discard obsolete queries. 401 retires the binding; 403 clears the feature; 404 detail removes its selection and list row. Network/DTO failures hide old content and support retry. OIDC 401 now invokes existing retirement subscribers before awaiting sign-in redirect.

## Verification commands

From `frontend`:

```sh
npm run test:timeline-review
npm run test:projects
npm run test:canvas
npm run test:publication
npm run test:render
npm test
npm run typecheck
npm run lint
npm run architecture:guard
npm run architecture:guard:test
npm run build -- --outDir /absolute/task-private/build
```

The focused group includes routeTree tests, which also overlap Projects/Canvas/Publication/Render groups. Do not sum group totals. Full tests are appropriate here because shared 401 notification was corrected. Build output must be redirected: the default Vite build target writes into backend static resources.

`TimelineBrowser.test.tsx` uses explicit fixture sources; `api.test.ts` exercises strict parsing and the real Axios interceptor chain with an adapter, not a real server. `session.test.ts` tests OIDC 401 before a pending redirect. Native browser evidence is separate.

## Real local validation

External evidence: `/home/user/Documents/workspace/audit-runs/FRONTEND_TIMELINE_REVIEW_20260913`.
The accepted backend image is `sha256:7f516a26dfee34fd4c1899f2f87d2120195eddd996c7701380bc0320756bc005`. The harness uses a task-private Podman internal network, PostgreSQL 15/Flyway, and local RSA issuer discovery/JWKS with the existing OAuth2 resource-server configuration. Identity/RBAC bootstrap is synthetic SQL in the private database; canonical revisions/review/comment are created via supported authenticated HTTP endpoints. Browser product requests are GET-only. RS256 credentials are injected through the existing local-development token transport, not a production IdP login. Forbidden and expired credentials are substituted into requests; the backend actually returns 403/401 (no fabricated HTTP responses).

Useful commands: `python3 <evidence>/start-backend.py`, `python3 <evidence>/seed-identity.py`, local issuer generation/container scripts, `python3 <evidence>/seed-timeline.py`, `npm run dev -- --config <evidence>/vite.config.mjs`, and `node <evidence>/browser.mjs`. The scripts are retained as the actual run record, including initial failed HMAC setup and host-isolated issuer attempt. See external HANDOFF for exact results and cleanup. Keys/config secrets are private external files, never repository content.

Local HMAC authentication excludes `/api/render/timeline-snapshots`, so it cannot create initial canonical revisions through that endpoint; the existing OAuth2 setup succeeds. This is a test-setup limitation, not a missing revision/review read contract. No backend patch or authentication bypass was used. Production OIDC redirect/renewal and production deployment are not accepted by this local run.


## Coordinator integration correction

The original candidate is preserved in merge ancestry. Shared OIDC401 handling now captures request credential/session revision and checks it against current SDK state before retiring subscribers or redirecting. Old-session/old-token failures and aborted checks cannot retire a newer binding; concurrent failures for one rejected credential produce one automatic expiry action. New requests using that retired credential are cancelled until new credentials or a successful authentication callback establish a usable session. Redirect failure preserves the original HTTP401. UserManager remains the credential store, and the existing Axios client remains the transport.

R1 backend corrections retain distinct reviewer decisions without changing the read DTO. The browser renders each decision by its own ID; a two-approver fixture covers this integration. Decision counts/history do not establish permission or a new consensus rule. Actual production OIDC redirect/renewal and native populated thread/decision browser cases remain NOT_RUN; the new race coverage uses the actual Axios interceptor and OIDC subscription code with an explicit SDK fixture.
