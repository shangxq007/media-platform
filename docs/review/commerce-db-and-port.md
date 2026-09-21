# EP11 Commerce database and public checkout contracts

Commerce's commercial Product is `ProductCatalogEntry` in `commerce_product`, with
`commercial_offering` and `product_catalog_command`. The delivered ProductCatalogAuthority /
ProductCatalogJdbcRepository pair remains its single command/SQL authority. The earlier H5
catalog convergence (e8f084f2) already removed a need to recreate catalog persistence. The C8
commercial authority contract explicitly separates this catalog from technical media objects.

The historical Render ProductRepository pointer instead writes `product`: RAW_MEDIA,
FINAL_RENDER and other producer build results with Storage references, provenance and dependency
graphs. It does not write `commerce_product`. Its existing runtime path is not a competing
Commerce catalog lifecycle and is unchanged in this EP. Moving it into Commerce would make
Commerce own media execution semantics. No technical Product, Artifact or Media ownership is
reinterpreted and no released migration is changed.

| Authority/entry | Owner | Reachable callers | Public boundary / disposition |
|---|---|---|---|
| ProductCatalogAuthority / ProductCatalogJdbcRepository | Commerce commercial catalog | CommerceCatalogService and existing catalog command composition | Existing sole authority retained; foreign catalog SQL/repository imports guarded |
| ProductRuntimeService / technical ProductRepository | Existing technical Product runtime | Render preview, output, input and Timeline adapters | Preserved distinct technical object, not a commercial Product writer |
| CheckoutPaymentPort | Commerce initiation contract; Payment mechanics implementation | CheckoutOrchestrator → Payment CheckoutPaymentPortAdapter | Moved to commerce.api.checkout; old commerce.app definition/import removed |
| CheckoutOrderPort | Commerce order confirmation | Payment settlement composition → CheckoutOrchestrator | Public interface exposing the existing persisted-checkout confirmation, without caller actor override |
| PaymentSettlementProjectionPort / PaymentOutboxDispatcher | Payment-local durable settlement handoff | Platform PaymentSettledCheckoutProjectionHandler | Retained; no second settlement/event framework |

The same CheckoutOrchestrator implements the order port. The adapter no longer imports Commerce
application implementation. Payment-local transaction/claim, provider fencing and settlement
Outbox remain unchanged. A controlled provider test exercises actual initiation, durable replay,
changed-payload rejection, webhook replay, transactional handoff rollback and successful retry.
Provider I/O is not atomically committed with PostgreSQL, and no such claim is made.

Review of the actual scheduled handoff reproduced a missing tenant context: the durable row has
a tenant but the scheduler thread has no HTTP context, so Commerce's scoped repository rejected
the lookup. The composition now establishes the tenant from the claimed durable Payment fact,
rejects a conflicting preexisting context and restores it in finally on success/failure. Commerce
resolves the principal from the persisted checkout; the handoff cannot supply an actor override.
Wrong tenant/resource leaves no order and preserves pending recovery. No new HTTP Product or
checkout endpoint, authorization decision, principal substitution or actor-hint field is added.
This is not a new acceptance of the unrelated existing HTTP Commerce/catalog command surface.

No transaction synchronization is introduced: the Payment dispatcher already encloses claim,
Commerce effects and dispatched acknowledgement in its database transaction. Tests inject a
fulfillment failure and assert zero order plus a pending handoff, then one order after retry.
No synthetic consumer, fallback writer, compatibility alias, shared-kernel business contract,
new event, database migration or generated schema edit is introduced.

`CommercePublicBoundaryTest` guards foreign writes/imports of the commercial catalog and Payment
imports of Commerce internals, including negative reintroduction examples. Real assembled tests
check exactly one implementation for each new public contract. Existing Commerce catalog CAS,
concurrent command, rollback, replay and applicability tests and Payment authority recovery tests
remain relevant; external task evidence records exact execution and review scope.
