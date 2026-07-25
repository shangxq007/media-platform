package com.example.platform.identity.infrastructure;

import com.example.platform.identity.domain.WorkspaceMember;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.WorkspaceMember.WORKSPACE_MEMBER;


@Repository

public class WorkspaceMemberRepository {

    private final DSLContext dsl;

    public WorkspaceMemberRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public WorkspaceMember save(WorkspaceMember member) {
        dsl.insertInto(WORKSPACE_MEMBER)
                .columns(WORKSPACE_MEMBER.ID, WORKSPACE_MEMBER.WORKSPACE_ID, WORKSPACE_MEMBER.USER_ID,
                        WORKSPACE_MEMBER.ROLE, WORKSPACE_MEMBER.STATUS, WORKSPACE_MEMBER.JOINED_AT, WORKSPACE_MEMBER.UPDATED_AT)
                .values(member.id(), member.workspaceId(), member.userId(),
                        member.role(), member.status().name(),
                        LocalDateTime.ofInstant(member.joinedAt(), ZoneOffset.UTC),
                        LocalDateTime.ofInstant(member.updatedAt(), ZoneOffset.UTC))
                .execute();
        return member;
    }

    public Optional<WorkspaceMember> findById(String id) {
        Record record = dsl.select()
                .from(WORKSPACE_MEMBER)
                .where(WORKSPACE_MEMBER.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<WorkspaceMember> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(WORKSPACE_MEMBER)
                .where(WORKSPACE_MEMBER.WORKSPACE_ID.eq(workspaceId))
                .orderBy(WORKSPACE_MEMBER.JOINED_AT.desc())
                .fetch(this::mapRecord);
    }

    public Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId) {
        Record record = dsl.select()
                .from(WORKSPACE_MEMBER)
                .where(WORKSPACE_MEMBER.WORKSPACE_ID.eq(workspaceId))
                .and(WORKSPACE_MEMBER.USER_ID.eq(userId))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public void updateRole(String id, String role, OffsetDateTime updatedAt) {
        dsl.update(WORKSPACE_MEMBER)
                .set(WORKSPACE_MEMBER.ROLE, role)
                .set(WORKSPACE_MEMBER.UPDATED_AT, updatedAt.toLocalDateTime())
                .where(WORKSPACE_MEMBER.ID.eq(id))
                .execute();
    }

    public void updateStatus(String id, WorkspaceMember.MemberStatus status, OffsetDateTime updatedAt) {
        dsl.update(WORKSPACE_MEMBER)
                .set(WORKSPACE_MEMBER.STATUS, status.name())
                .set(WORKSPACE_MEMBER.UPDATED_AT, updatedAt.toLocalDateTime())
                .where(WORKSPACE_MEMBER.ID.eq(id))
                .execute();
    }

    private WorkspaceMember mapRecord(Record record) {
        return new WorkspaceMember(
                record.get(WORKSPACE_MEMBER.ID, String.class),
                record.get(WORKSPACE_MEMBER.WORKSPACE_ID, String.class),
                record.get(WORKSPACE_MEMBER.USER_ID, String.class),
                record.get(WORKSPACE_MEMBER.ROLE, String.class),
                WorkspaceMember.MemberStatus.valueOf(record.get(WORKSPACE_MEMBER.STATUS, String.class)),
                record.get(WORKSPACE_MEMBER.JOINED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                record.get(WORKSPACE_MEMBER.UPDATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }
}
