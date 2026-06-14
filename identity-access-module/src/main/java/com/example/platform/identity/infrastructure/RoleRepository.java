package com.example.platform.identity.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.GroupRoleAssignment;
import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.domain.Role;
import com.example.platform.identity.domain.RolePermission;
import com.example.platform.identity.domain.UserRoleAssignment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

@Repository

public class RoleRepository {

    private final DSLContext dsl;

    public RoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Role save(Role role) {
        dsl.insertInto(table("role"))
                .columns(field("id"), field("role_key"), field("name"),
                        field("description"), field("scope"), field("created_at"))
                .values(role.id(), role.roleKey(), role.name(),
                        role.description(), role.scope().name(), role.createdAt())
                .execute();
        return role;
    }

    public Optional<Role> findById(String id) {
        Record record = dsl.select()
                .from(table("role"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRoleRecord);
    }

    public Optional<Role> findByKey(String roleKey) {
        Record record = dsl.select()
                .from(table("role"))
                .where(field("role_key").eq(roleKey))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRoleRecord);
    }

    public List<Role> findAll() {
        return dsl.select()
                .from(table("role"))
                .orderBy(field("created_at").asc())
                .fetch(this::mapRoleRecord);
    }

    public List<Role> findByScope(Role.RoleScope scope) {
        return dsl.select()
                .from(table("role"))
                .where(field("scope").eq(scope.name()))
                .orderBy(field("created_at").asc())
                .fetch(this::mapRoleRecord);
    }

    public Permission savePermission(Permission permission) {
        dsl.insertInto(table("permission"))
                .columns(field("id"), field("permission_key"), field("name"),
                        field("description"), field("resource_type"), field("created_at"))
                .values(permission.id(), permission.permissionKey(), permission.name(),
                        permission.description(), permission.resourceType(), permission.createdAt())
                .execute();
        return permission;
    }

    public Optional<Permission> findPermissionByKey(String permissionKey) {
        Record record = dsl.select()
                .from(table("permission"))
                .where(field("permission_key").eq(permissionKey))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapPermissionRecord);
    }

    public List<Permission> findAllPermissions() {
        return dsl.select()
                .from(table("permission"))
                .orderBy(field("created_at").asc())
                .fetch(this::mapPermissionRecord);
    }

    public RolePermission saveRolePermission(RolePermission rolePermission) {
        dsl.insertInto(table("role_permission"))
                .columns(field("id"), field("role_id"), field("permission_id"), field("created_at"))
                .values(rolePermission.id(), rolePermission.roleId(),
                        rolePermission.permissionId(), rolePermission.createdAt())
                .execute();
        return rolePermission;
    }

    public List<Permission> findPermissionsByRoleId(String roleId) {
        return dsl.select()
                .from(table("permission"))
                .join(table("role_permission"))
                .on(field("permission.id").eq(field("role_permission.permission_id")))
                .where(field("role_permission.role_id").eq(roleId))
                .fetch(r -> mapPermissionRecord(r));
    }

    public UserRoleAssignment saveUserRoleAssignment(UserRoleAssignment assignment) {
        dsl.insertInto(table("user_role_assignment"))
                .columns(field("id"), field("tenant_id"), field("workspace_id"),
                        field("user_id"), field("role_id"), field("assigned_by"), field("created_at"))
                .values(assignment.id(), assignment.tenantId(), assignment.workspaceId(),
                        assignment.userId(), assignment.roleId(),
                        assignment.assignedBy(), assignment.createdAt())
                .execute();
        return assignment;
    }

    public List<UserRoleAssignment> findUserRoleAssignmentsByUserId(String userId) {
        return dsl.select()
                .from(table("user_role_assignment"))
                .where(field("user_id").eq(userId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapUserRoleAssignmentRecord);
    }

    public List<UserRoleAssignment> findUserRoleAssignmentsByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(table("user_role_assignment"))
                .where(field("workspace_id").eq(workspaceId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapUserRoleAssignmentRecord);
    }

    /**
     * Delete role assignments for a user across ALL workspaces matching the role key.
     * Used by dev/bootstrap to clear a user's roles before re-assigning.
     *
     * @deprecated Use {@link #deleteUserRoleAssignmentByWorkspace(String, String, String)} for
     *             workspace-scoped revocation to avoid cross-workspace data loss.
     */
    @Deprecated
    public void deleteUserRoleAssignment(String userId, String roleKey) {
        dsl.deleteFrom(table("user_role_assignment"))
                .where(field("user_id").eq(userId))
                .and(field("role_id").in(
                        dsl.select(field("id"))
                                .from(table("role"))
                                .where(field("role_key").eq(roleKey))))
                .execute();
    }

    /**
     * Delete a role assignment for a user in a SPECIFIC workspace.
     * This is the correct method for workspace-scoped role revocation.
     *
     * @param userId      the user ID
     * @param roleKey     the role key (e.g. "ADMIN")
     * @param workspaceId the workspace ID scope
     */
    public void deleteUserRoleAssignmentByWorkspace(String userId, String roleKey, String workspaceId) {
        dsl.deleteFrom(table("user_role_assignment"))
                .where(field("user_id").eq(userId))
                .and(field("workspace_id").eq(workspaceId))
                .and(field("role_id").in(
                        dsl.select(field("id"))
                                .from(table("role"))
                                .where(field("role_key").eq(roleKey))))
                .execute();
    }

    public GroupRoleAssignment saveGroupRoleAssignment(GroupRoleAssignment assignment) {
        dsl.insertInto(table("group_role_assignment"))
                .columns(field("id"), field("workspace_id"), field("group_id"),
                        field("role_id"), field("assigned_at"))
                .values(assignment.id(), assignment.workspaceId(), assignment.groupId(),
                        assignment.roleId(), assignment.assignedAt())
                .execute();
        return assignment;
    }

    public List<GroupRoleAssignment> findGroupRoleAssignmentsByGroupId(String groupId) {
        return dsl.select()
                .from(table("group_role_assignment"))
                .where(field("group_id").eq(groupId))
                .orderBy(field("assigned_at").desc())
                .fetch(this::mapGroupRoleAssignmentRecord);
    }

    private Role mapRoleRecord(Record record) {
        return new Role(
                record.get(field("id"), String.class),
                record.get(field("role_key"), String.class),
                record.get(field("name"), String.class),
                record.get(field("description"), String.class),
                Role.RoleScope.valueOf(record.get(field("scope"), String.class)),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }

    private Permission mapPermissionRecord(Record record) {
        return new Permission(
                record.get(field("id"), String.class),
                record.get(field("permission_key"), String.class),
                record.get(field("name"), String.class),
                record.get(field("description"), String.class),
                record.get(field("resource_type"), String.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }

    private UserRoleAssignment mapUserRoleAssignmentRecord(Record record) {
        return new UserRoleAssignment(
                record.get(field("id"), String.class),
                record.get(field("tenant_id"), String.class),
                record.get(field("workspace_id"), String.class),
                record.get(field("user_id"), String.class),
                record.get(field("role_id"), String.class),
                record.get(field("assigned_by"), String.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }

    private GroupRoleAssignment mapGroupRoleAssignmentRecord(Record record) {
        return new GroupRoleAssignment(
                record.get(field("id"), String.class),
                record.get(field("workspace_id"), String.class),
                record.get(field("group_id"), String.class),
                record.get(field("role_id"), String.class),
                record.get(field("assigned_at"), OffsetDateTime.class).toInstant()
        );
    }
}
