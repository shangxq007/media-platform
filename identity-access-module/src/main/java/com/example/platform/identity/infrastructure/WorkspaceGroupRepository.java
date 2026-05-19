package com.example.platform.identity.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.WorkspaceGroup;
import com.example.platform.identity.domain.WorkspaceGroupMember;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class WorkspaceGroupRepository {

    private final DSLContext dsl;

    public WorkspaceGroupRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public WorkspaceGroup save(WorkspaceGroup group) {
        dsl.insertInto(table("workspace_group"))
                .columns(field("id"), field("workspace_id"), field("name"),
                        field("description"), field("created_at"))
                .values(group.id(), group.workspaceId(), group.name(),
                        group.description(), group.createdAt())
                .execute();
        return group;
    }

    public Optional<WorkspaceGroup> findById(String id) {
        Record record = dsl.select()
                .from(table("workspace_group"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapGroupRecord);
    }

    public List<WorkspaceGroup> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(table("workspace_group"))
                .where(field("workspace_id").eq(workspaceId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapGroupRecord);
    }

    public WorkspaceGroupMember addMember(WorkspaceGroupMember groupMember) {
        dsl.insertInto(table("workspace_group_member"))
                .columns(field("id"), field("workspace_id"), field("group_id"),
                        field("member_id"), field("created_at"))
                .values(groupMember.id(), groupMember.workspaceId(), groupMember.groupId(),
                        groupMember.memberId(), groupMember.createdAt())
                .execute();
        return groupMember;
    }

    public List<WorkspaceGroupMember> findMembersByGroupId(String groupId) {
        return dsl.select()
                .from(table("workspace_group_member"))
                .where(field("group_id").eq(groupId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapGroupMemberRecord);
    }

    private WorkspaceGroup mapGroupRecord(Record record) {
        return new WorkspaceGroup(
                record.get(field("id"), String.class),
                record.get(field("workspace_id"), String.class),
                record.get(field("name"), String.class),
                record.get(field("description"), String.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }

    private WorkspaceGroupMember mapGroupMemberRecord(Record record) {
        return new WorkspaceGroupMember(
                record.get(field("id"), String.class),
                record.get(field("workspace_id"), String.class),
                record.get(field("group_id"), String.class),
                record.get(field("member_id"), String.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }
}
