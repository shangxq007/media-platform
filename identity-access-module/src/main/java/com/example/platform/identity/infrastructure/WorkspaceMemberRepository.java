package com.example.platform.identity.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.WorkspaceMember;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class WorkspaceMemberRepository {

    private final DSLContext dsl;

    public WorkspaceMemberRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public WorkspaceMember save(WorkspaceMember member) {
        dsl.insertInto(table("workspace_member"))
                .columns(field("id"), field("workspace_id"), field("user_id"),
                        field("role"), field("status"), field("joined_at"), field("updated_at"))
                .values(member.id(), member.workspaceId(), member.userId(),
                        member.role(), member.status().name(),
                        member.joinedAt(), member.updatedAt())
                .execute();
        return member;
    }

    public Optional<WorkspaceMember> findById(String id) {
        Record record = dsl.select()
                .from(table("workspace_member"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<WorkspaceMember> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(table("workspace_member"))
                .where(field("workspace_id").eq(workspaceId))
                .orderBy(field("joined_at").desc())
                .fetch(this::mapRecord);
    }

    public Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId) {
        Record record = dsl.select()
                .from(table("workspace_member"))
                .where(field("workspace_id").eq(workspaceId))
                .and(field("user_id").eq(userId))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public void updateRole(String id, String role, OffsetDateTime updatedAt) {
        dsl.update(table("workspace_member"))
                .set(field("role"), role)
                .set(field("updated_at"), updatedAt)
                .where(field("id").eq(id))
                .execute();
    }

    public void updateStatus(String id, WorkspaceMember.MemberStatus status, OffsetDateTime updatedAt) {
        dsl.update(table("workspace_member"))
                .set(field("status"), status.name())
                .set(field("updated_at"), updatedAt)
                .where(field("id").eq(id))
                .execute();
    }

    private WorkspaceMember mapRecord(Record record) {
        return new WorkspaceMember(
                record.get(field("id"), String.class),
                record.get(field("workspace_id"), String.class),
                record.get(field("user_id"), String.class),
                record.get(field("role"), String.class),
                WorkspaceMember.MemberStatus.valueOf(record.get(field("status"), String.class)),
                record.get(field("joined_at"), OffsetDateTime.class).toInstant(),
                record.get(field("updated_at"), OffsetDateTime.class).toInstant()
        );
    }
}
