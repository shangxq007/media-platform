package com.example.platform.identity.infrastructure;

import com.example.platform.identity.domain.WorkspaceGroup;
import com.example.platform.identity.domain.WorkspaceGroupMember;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.WorkspaceGroup.WORKSPACE_GROUP;
import static com.example.platform.typedschema.jooq.generated.tables.WorkspaceGroupMember.WORKSPACE_GROUP_MEMBER;


@Repository

public class WorkspaceGroupRepository {

    private final DSLContext dsl;

    public WorkspaceGroupRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public WorkspaceGroup save(WorkspaceGroup group) {
        dsl.insertInto(WORKSPACE_GROUP)
                .columns(WORKSPACE_GROUP.ID, WORKSPACE_GROUP.WORKSPACE_ID, WORKSPACE_GROUP.NAME,
                        WORKSPACE_GROUP.DESCRIPTION, WORKSPACE_GROUP.CREATED_AT)
                .values(group.id(), group.workspaceId(), group.name(),
                        group.description(),
                        LocalDateTime.ofInstant(group.createdAt(), ZoneOffset.UTC))
                .execute();
        return group;
    }

    public Optional<WorkspaceGroup> findById(String id) {
        Record record = dsl.select()
                .from(WORKSPACE_GROUP)
                .where(WORKSPACE_GROUP.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapGroupRecord);
    }

    public List<WorkspaceGroup> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(WORKSPACE_GROUP)
                .where(WORKSPACE_GROUP.WORKSPACE_ID.eq(workspaceId))
                .orderBy(WORKSPACE_GROUP.CREATED_AT.desc())
                .fetch(this::mapGroupRecord);
    }

    public WorkspaceGroupMember addMember(WorkspaceGroupMember groupMember) {
        dsl.insertInto(WORKSPACE_GROUP_MEMBER)
                .columns(WORKSPACE_GROUP_MEMBER.ID, WORKSPACE_GROUP_MEMBER.WORKSPACE_ID, WORKSPACE_GROUP_MEMBER.GROUP_ID,
                        WORKSPACE_GROUP_MEMBER.MEMBER_ID, WORKSPACE_GROUP_MEMBER.CREATED_AT)
                .values(groupMember.id(), groupMember.workspaceId(), groupMember.groupId(),
                        groupMember.memberId(),
                        LocalDateTime.ofInstant(groupMember.createdAt(), ZoneOffset.UTC))
                .execute();
        return groupMember;
    }

    public List<WorkspaceGroupMember> findMembersByGroupId(String groupId) {
        return dsl.select()
                .from(WORKSPACE_GROUP_MEMBER)
                .where(WORKSPACE_GROUP_MEMBER.GROUP_ID.eq(groupId))
                .orderBy(WORKSPACE_GROUP.CREATED_AT.desc())
                .fetch(this::mapGroupMemberRecord);
    }

    private WorkspaceGroup mapGroupRecord(Record record) {
        return new WorkspaceGroup(
                record.get(WORKSPACE_GROUP.ID, String.class),
                record.get(WORKSPACE_GROUP.WORKSPACE_ID, String.class),
                record.get(WORKSPACE_GROUP.NAME, String.class),
                record.get(WORKSPACE_GROUP.DESCRIPTION, String.class),
                record.get(WORKSPACE_GROUP.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }

    private WorkspaceGroupMember mapGroupMemberRecord(Record record) {
        return new WorkspaceGroupMember(
                record.get(WORKSPACE_GROUP_MEMBER.ID, String.class),
                record.get(WORKSPACE_GROUP_MEMBER.WORKSPACE_ID, String.class),
                record.get(WORKSPACE_GROUP_MEMBER.GROUP_ID, String.class),
                record.get(WORKSPACE_GROUP_MEMBER.MEMBER_ID, String.class),
                record.get(WORKSPACE_GROUP_MEMBER.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }
}
