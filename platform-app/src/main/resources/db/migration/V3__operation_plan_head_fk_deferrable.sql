-- OPERATION_PLAN_TRANSACTION_FINAL_EVIDENCE_VERIFICATION_V1 (EV1_C correction):
-- head FK must be DEFERRABLE INITIALLY DEFERRED so the apply transaction may
-- CAS-advance head to the new (not-yet-inserted) revision id and insert the
-- revision later in the SAME transaction. The FK remains fully active: it is
-- validated at COMMIT, so omitting the revision INSERT fails the commit
-- (proven by dedicated probe test). Dropping/recreating is the minimal
-- in-place PostgreSQL mechanism (no table rewrite).

alter table timeline_revision_ref
    drop constraint fk_timeline_revision_ref_head;

alter table timeline_revision_ref
    add constraint fk_timeline_revision_ref_head
    foreign key (head_revision_id) references timeline_revision(id)
    deferrable initially deferred;
