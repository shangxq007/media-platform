package com.example.platform.outbox.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.platform.typedschema.jooq.generated.tables.OutboxEvents.OUTBOX_EVENTS;
import org.jooq.impl.DSL;


@Service
public class OutboxEventService {
    private final OutboxEventRouter router;
    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);
    static final long BASE_BACKOFF_MS = 1000L;

    /** Status: event is waiting to be processed. */
    public static final String STATUS_PENDING = "PENDING";
    /** Status: event is currently being processed (locked). */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** Status: event was successfully dispatched. */
    public static final String STATUS_PROCESSED = "PROCESSED";
    /** Status: event failed, may be retried. */
    public static final String STATUS_FAILED = "FAILED";
    /** Status: event exceeded max retries, no more attempts. */
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    private final DSLContext dsl;
    private final int maxRetries;
    private final PostgresNotificationService notifyService;
    @Value("${app.outbox.claim-lease-ms:60000}")
    private long claimLeaseMs=60000L;
    public long claimLeaseMillis() {
        if(claimLeaseMs<1000 || claimLeaseMs>3600000)throw new IllegalArgumentException("Outbox claim lease must be between 1 second and 1 hour");
        return claimLeaseMs;
    }

    public OutboxEventService(DSLContext dsl,
            @Value("${app.outbox.max-retries:3}") int maxRetries,
            PostgresNotificationService notifyService, OutboxEventRouter router) {
        this.dsl = dsl;
        this.router = router;
        if (maxRetries < 1 || maxRetries > 30) throw new IllegalArgumentException("Outbox max retries must be between 1 and 30");
        this.maxRetries = maxRetries;
        this.notifyService = notifyService;
    }

    // -------------------------------------------------------------------------
    // Overview / Queries
    // -------------------------------------------------------------------------

    public Map<String, Object> overview() {
        Integer pending = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING))
        );
        Integer processing = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSING))
        );
        Integer processed = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSED))
        );
        Integer failed = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED))
        );
        Integer deadLetter = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_DEAD_LETTER))
        );
        return Map.of(
                "module", "outbox-event-module",
                "status", "active",
                "description", "Outbox event module — persistence, dispatch, and retry.",
                "pending", pending,
                "processing", processing,
                "processed", processed,
                "failed", failed,
                "deadLetter", deadLetter
        );
    }

    public List<Map<String, Object>> recent(int limit) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.LAST_ERROR_CODE,
                        OUTBOX_EVENTS.LAST_ERROR_MESSAGE,
                        OUTBOX_EVENTS.NEXT_ATTEMPT_AT,
                        OUTBOX_EVENTS.LOCKED_AT,
                        OUTBOX_EVENTS.LOCKED_BY,
                        OUTBOX_EVENTS.CREATED_AT,
                        OUTBOX_EVENTS.PUBLISHED_AT
                )
                .from(OUTBOX_EVENTS)
                .orderBy(OUTBOX_EVENTS.CREATED_AT.desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> deadLetterEvents(int limit) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.LAST_ERROR_CODE,
                        OUTBOX_EVENTS.LAST_ERROR_MESSAGE,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_DEAD_LETTER))
                .orderBy(OUTBOX_EVENTS.CREATED_AT.desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> failedEvents(int limit) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.LAST_ERROR_CODE,
                        OUTBOX_EVENTS.LAST_ERROR_MESSAGE,
                        OUTBOX_EVENTS.NEXT_ATTEMPT_AT,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED))
                .orderBy(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.asc())
                .limit(limit)
                .fetchMaps();
    }

    /**
     * Returns events eligible for dispatch: PENDING with no future backoff,
     * or FAILED with next_attempt_at <= now and retry_count < max_retries.
     */
    public List<Map<String, Object>> pendingForDispatch(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.PAYLOAD,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.IDEMPOTENCY_KEY,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(
                        OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING)
                                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.isNull()
                                        .or(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now)))
                                .or(
                                        OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED)
                                                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now))
                                                .and(OUTBOX_EVENTS.RETRY_COUNT.lt(OUTBOX_EVENTS.MAX_RETRIES))
                                )
                )
                .orderBy(OUTBOX_EVENTS.CREATED_AT.asc())
                .limit(limit)
                .fetchMaps();
    }

    /**
     * Read a single event by ID (unlocked, for reads after locking).
     */
    public Map<String,Object> readEvent(String outboxId) {return readEventWhere(OUTBOX_EVENTS.ID.eq(outboxId));}
    public Map<String,Object> readClaimedEvent(OutboxClaim claim) {return readEventWhere(ownsClaim(claim,true));}
    private Map<String,Object> readEventWhere(org.jooq.Condition condition) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.PAYLOAD,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.IDEMPOTENCY_KEY,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(condition)
                .fetchOneMap();
    }

    // -------------------------------------------------------------------------
    // Append with idempotency
    // -------------------------------------------------------------------------

    @Transactional
    public String append(com.example.platform.outbox.api.event.OutboxAppend<?> append) {
        return appendUsing(append,dsl,notifyService::notifyOutboxEvent);
    }

    /** Join an already-active owner jOOQ transaction; never start an independent transaction. */
    public String appendInTransaction(com.example.platform.outbox.api.event.OutboxAppend<?> append, DSLContext transaction) {
        java.util.Objects.requireNonNull(transaction,"owner transaction required");
        transaction.connection(connection->{if(connection.getAutoCommit())throw new IllegalStateException("active owner transaction required");});
        return appendUsing(append,transaction,()->notifyService.notifyOutboxEvent(transaction));
    }

    private String appendUsing(com.example.platform.outbox.api.event.OutboxAppend<?> append,DSLContext dsl,Runnable notify) {
        String currentTenant = com.example.platform.shared.web.TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(append.tenantId()))
            throw new IllegalArgumentException("Outbox tenant scope mismatch");
        String payload = router.encode(append);
        String key = append.idempotencyKey();
        if (key != null && key.isBlank()) key = null;
        // Canonical schema has a non-unique idempotency index. Serialize same-key appends
        // in the existing domain/Spring transaction; no schema-error retry or second write path.
        if (key != null) dsl.execute("select pg_advisory_xact_lock(hashtextextended(?, 0))", key);
        var existing = key == null ? null : dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.IDEMPOTENCY_KEY.eq(key)).forUpdate().fetchOne();
        if (existing == null) {
            String id = "obx_" + java.util.UUID.randomUUID().toString().replace("-", "");
            dsl.insertInto(OUTBOX_EVENTS)
                    .columns(OUTBOX_EVENTS.ID, OUTBOX_EVENTS.AGGREGATE_TYPE, OUTBOX_EVENTS.AGGREGATE_ID,
                            OUTBOX_EVENTS.EVENT_TYPE, OUTBOX_EVENTS.EVENT_VERSION, OUTBOX_EVENTS.PAYLOAD,
                            OUTBOX_EVENTS.STATUS, OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.MAX_RETRIES,
                            OUTBOX_EVENTS.IDEMPOTENCY_KEY, OUTBOX_EVENTS.CREATED_AT)
                    .values(id, append.type().aggregateType(), append.aggregateId(), append.type().name(), append.type().version(),
                            payload, STATUS_PENDING, 0, maxRetries, key, LocalDateTime.now()).execute();
            notify.run();
            return id;
        }
        var decoded = router.decode(existing.getEventType(), existing.getEventVersion(), existing.getAggregateType(), existing.getAggregateId(), existing.getPayload());
        if (!append.tenantId().equals(decoded.tenantId()) || !append.type().name().equals(existing.getEventType())
                || append.type().version() != existing.getEventVersion()
                || !append.type().aggregateType().equals(existing.getAggregateType()) || !append.aggregateId().equals(existing.getAggregateId()))
            throw new IllegalArgumentException("Idempotency key belongs to a different event scope");
        if (STATUS_PENDING.equals(existing.getStatus()) || STATUS_FAILED.equals(existing.getStatus())) {
            dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.PAYLOAD, payload).set(OUTBOX_EVENTS.STATUS, STATUS_PENDING)
                    .set(OUTBOX_EVENTS.RETRY_COUNT, 0).set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT, (LocalDateTime) null)
                    .set(OUTBOX_EVENTS.LAST_ERROR_CODE, (String) null).set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE, (String) null)
                    .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null).set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                    .where(OUTBOX_EVENTS.ID.eq(existing.getId())).execute();
        }
        return existing.getId();
    }

    private org.jooq.Condition ownsClaim(OutboxClaim claim,boolean fresh) {
        java.util.Objects.requireNonNull(claim);
        var condition=OUTBOX_EVENTS.ID.eq(claim.eventId()).and(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSING))
                .and(OUTBOX_EVENTS.LOCKED_BY.eq(claim.token()));
        Instant cutoff=Instant.now().minusMillis(claimLeaseMillis());
        return condition.and(fresh?OUTBOX_EVENTS.LOCKED_AT.gt(cutoff):OUTBOX_EVENTS.LOCKED_AT.le(cutoff));
    }

    /** Atomic acquisition; a process identifier is diagnostic input, never the claim identity. */
    @Transactional
    public java.util.Optional<OutboxClaim> claimForProcessing(String outboxId,String processorId) {
        if(processorId==null || processorId.isBlank())throw new IllegalArgumentException("processor required");
        claimLeaseMillis();
        var now=LocalDateTime.now();String token=java.util.UUID.randomUUID().toString();
        var eligible=OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING)
                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.isNull().or(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now)))
                .or(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED).and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now))
                        .and(OUTBOX_EVENTS.RETRY_COUNT.lt(OUTBOX_EVENTS.MAX_RETRIES)));
        int changed=dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS,STATUS_PROCESSING)
                .set(OUTBOX_EVENTS.LOCKED_AT,Instant.now()).set(OUTBOX_EVENTS.LOCKED_BY,token)
                .where(OUTBOX_EVENTS.ID.eq(outboxId)).and(eligible).execute();
        return changed==1?java.util.Optional.of(new OutboxClaim(outboxId,token)):java.util.Optional.empty();
    }
    @Transactional
    public boolean renewClaim(OutboxClaim claim) {
        return dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.LOCKED_AT,Instant.now()).where(ownsClaim(claim,true)).execute()==1;
    }
    @Transactional
    public boolean markProcessed(OutboxClaim claim) {
        return dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS,STATUS_PROCESSED)
                .set(OUTBOX_EVENTS.PUBLISHED_AT,LocalDateTime.now())
                .set(OUTBOX_EVENTS.LOCKED_AT,(Instant)null).set(OUTBOX_EVENTS.LOCKED_BY,(String)null)
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE,(String)null).set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE,(String)null)
                .where(ownsClaim(claim,true)).execute()==1;
    }
    @Transactional
    public boolean markFailedWithDetails(OutboxClaim claim,String code,String message) {
        return failClaim(claim,code,message,true);
    }
    private boolean failClaim(OutboxClaim claim,String code,String message,boolean fresh) {
        var condition=ownsClaim(claim,fresh);
        var row=dsl.select(OUTBOX_EVENTS.RETRY_COUNT,OUTBOX_EVENTS.MAX_RETRIES).from(OUTBOX_EVENTS)
                .where(condition).forUpdate().fetchOne();
        if(row==null)return false;
        Integer retries=row.get(OUTBOX_EVENTS.RETRY_COUNT),maximum=row.get(OUTBOX_EVENTS.MAX_RETRIES);
        if(retries==null || retries<0 || maximum==null || maximum<1 || maximum>30)
            return quarantineWhere(condition,"INVALID_RETRY_POLICY","Missing or invalid persisted retry policy");
        int next=retries+1;boolean terminal=next>=maximum;
        LocalDateTime due=terminal?null:LocalDateTime.now().plusNanos(BASE_BACKOFF_MS*(1L<<next)*1000000L);
        return dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.RETRY_COUNT,next)
                .set(OUTBOX_EVENTS.STATUS,terminal?STATUS_DEAD_LETTER:STATUS_FAILED)
                .set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT,due)
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE,code).set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE,message)
                .set(OUTBOX_EVENTS.LOCKED_AT,(Instant)null).set(OUTBOX_EVENTS.LOCKED_BY,(String)null)
                .where(condition).execute()==1;
    }
    /** Bounded abandoned-claim recovery; live claims are renewed and never reset indiscriminately. */
    @Transactional
    public int recoverExpiredClaims(int limit) {
        if(limit<1 || limit>1000)throw new IllegalArgumentException("recovery limit must be 1..1000");
        Instant cutoff=Instant.now().minusMillis(claimLeaseMillis());int recovered=0;
        var rows=dsl.select(OUTBOX_EVENTS.ID,OUTBOX_EVENTS.LOCKED_BY,OUTBOX_EVENTS.LOCKED_AT).from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSING))
                .and(OUTBOX_EVENTS.LOCKED_AT.le(cutoff).or(OUTBOX_EVENTS.LOCKED_AT.isNull()).or(OUTBOX_EVENTS.LOCKED_BY.isNull()))
                .orderBy(OUTBOX_EVENTS.LOCKED_AT.asc()).limit(limit).forUpdate().skipLocked().fetch();
        for(var row:rows) {
            String id=row.get(OUTBOX_EVENTS.ID),token=row.get(OUTBOX_EVENTS.LOCKED_BY);
            if(token==null || row.get(OUTBOX_EVENTS.LOCKED_AT)==null) {
                if(quarantineWhere(OUTBOX_EVENTS.ID.eq(id).and(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSING)).and(OUTBOX_EVENTS.LOCKED_AT.isNull().or(OUTBOX_EVENTS.LOCKED_BY.isNull())),"INVALID_CLAIM","Processing claim metadata is missing"))recovered++;
            } else if(failClaim(new OutboxClaim(id,token),"CLAIM_EXPIRED","Processing lease expired; replay required",false))recovered++;
        }
        return recovered;
    }
    @Transactional
    public int resetDueFailedEvents() {
        return dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS,STATUS_PENDING)
                .set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT,(LocalDateTime)null)
                .set(OUTBOX_EVENTS.LOCKED_AT,(Instant)null).set(OUTBOX_EVENTS.LOCKED_BY,(String)null)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED)).and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(LocalDateTime.now()))
                .and(OUTBOX_EVENTS.RETRY_COUNT.lt(OUTBOX_EVENTS.MAX_RETRIES)).execute();
    }
    /** Explicit administrative action, separate from a claimant's fenced quarantine. */
    @Transactional
    public void markDeadLetter(String outboxId,String reason) {
        quarantineWhere(OUTBOX_EVENTS.ID.eq(outboxId).and(OUTBOX_EVENTS.STATUS.ne(STATUS_PROCESSED)),"MANUAL",reason);
    }
    @Transactional
    public boolean quarantine(OutboxClaim claim,String code,String reason) {
        return quarantineWhere(ownsClaim(claim,true),code,reason);
    }
    private boolean quarantineWhere(org.jooq.Condition condition,String code,String reason) {
        return dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS,STATUS_DEAD_LETTER)
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE,code).set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE,reason)
                .set(OUTBOX_EVENTS.LOCKED_AT,(Instant)null).set(OUTBOX_EVENTS.LOCKED_BY,(String)null)
                .where(condition).execute()==1;
    }
}
