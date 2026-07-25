package com.example.platform.identity.infrastructure;

import com.example.platform.identity.domain.GroupRoleAssignment;
import com.example.platform.identity.domain.Permission;
import com.example.platform.identity.domain.Role;
import com.example.platform.identity.domain.RolePermission;
import com.example.platform.identity.domain.UserRoleAssignment;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.GroupRoleAssignment.GROUP_ROLE_ASSIGNMENT;
import static com.example.platform.typedschema.jooq.generated.tables.Permission.PERMISSION;
import static com.example.platform.typedschema.jooq.generated.tables.Role.ROLE;
import static com.example.platform.typedschema.jooq.generated.tables.RolePermission.ROLE_PERMISSION;
import static com.example.platform.typedschema.jooq.generated.tables.UserRoleAssignment.USER_ROLE_ASSIGNMENT;
import org.jooq.impl.DSL;


@Repository

public class RoleRepository {

    private final DSLContext dsl;

    public RoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Role save(Role role) {
        dsl.insertInto(ROLE)
                .columns(ROLE.ID, ROLE.ROLE_KEY, ROLE.NAME,
                        ROLE.DESCRIPTION, ROLE.SCOPE, ROLE.CREATED_AT)
                .values(role.id(), role.roleKey(), role.name(),
                        role.description(), role.scope().name(),
                        LocalDateTime.ofInstant(role.createdAt(), ZoneOffset.UTC))
                .execute();
        return role;
    }

    public Optional<Role> findById(String id) {
        Record record = dsl.select()
                .from(ROLE)
                .where(ROLE.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRoleRecord);
    }

    public Optional<Role> findByKey(String roleKey) {
        Record record = dsl.select()
                .from(ROLE)
                .where(ROLE.ROLE_KEY.eq(roleKey))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRoleRecord);
    }

    public List<Role> findAll() {
        return dsl.select()
                .from(ROLE)
                .orderBy(ROLE.CREATED_AT.asc())
                .fetch(this::mapRoleRecord);
    }

    public List<Role> findByScope(Role.RoleScope scope) {
        return dsl.select()
                .from(ROLE)
                .where(ROLE.SCOPE.eq(scope.name()))
                .orderBy(ROLE.CREATED_AT.asc())
                .fetch(this::mapRoleRecord);
    }

    public Permission savePermission(Permission permission) {
        dsl.insertInto(PERMISSION)
                .columns(PERMISSION.ID, PERMISSION.PERMISSION_KEY, PERMISSION.NAME,
                        PERMISSION.DESCRIPTION, PERMISSION.RESOURCE_TYPE, PERMISSION.CREATED_AT)
                .values(permission.id(), permission.permissionKey(), permission.name(),
                        permission.description(), permission.resourceType(),
                        LocalDateTime.ofInstant(permission.createdAt(), ZoneOffset.UTC))
                .execute();
        return permission;
    }

    public Optional<Permission> findPermissionByKey(String permissionKey) {
        Record record = dsl.select()
                .from(PERMISSION)
                .where(PERMISSION.PERMISSION_KEY.eq(permissionKey))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapPermissionRecord);
    }

    public List<Permission> findAllPermissions() {
        return dsl.select()
                .from(PERMISSION)
                .orderBy(PERMISSION.CREATED_AT.asc())
                .fetch(this::mapPermissionRecord);
    }

    public RolePermission saveRolePermission(RolePermission rolePermission) {
        dsl.insertInto(ROLE_PERMISSION)
                .columns(ROLE_PERMISSION.ID, ROLE_PERMISSION.ROLE_ID, ROLE_PERMISSION.PERMISSION_ID, ROLE_PERMISSION.CREATED_AT)
                .values(rolePermission.id(), rolePermission.roleId(),
                        rolePermission.permissionId(),
                        LocalDateTime.ofInstant(rolePermission.createdAt(), ZoneOffset.UTC))
                .execute();
        return rolePermission;
    }

    public List<Permission> findPermissionsByRoleId(String roleId) {
        return dsl.select()
                .from(PERMISSION)
                .join(ROLE_PERMISSION)
                .on(PERMISSION.ID.eq(ROLE_PERMISSION.PERMISSION_ID))
                .where(ROLE_PERMISSION.ROLE_ID.eq(roleId))
                .fetch(r -> mapPermissionRecord(r));
    }

    public UserRoleAssignment saveUserRoleAssignment(UserRoleAssignment assignment) {
        dsl.insertInto(USER_ROLE_ASSIGNMENT)
                .columns(USER_ROLE_ASSIGNMENT.ID, USER_ROLE_ASSIGNMENT.TENANT_ID, USER_ROLE_ASSIGNMENT.WORKSPACE_ID,
                        USER_ROLE_ASSIGNMENT.USER_ID, USER_ROLE_ASSIGNMENT.ROLE_ID, USER_ROLE_ASSIGNMENT.ASSIGNED_BY, USER_ROLE_ASSIGNMENT.CREATED_AT)
                .values(assignment.id(), assignment.tenantId(), assignment.workspaceId(),
                        assignment.userId(), assignment.roleId(),
                        assignment.assignedBy(),
                        LocalDateTime.ofInstant(assignment.createdAt(), ZoneOffset.UTC))
                .execute();
        return assignment;
    }

    public List<UserRoleAssignment> findUserRoleAssignmentsByUserId(String userId) {
        return dsl.select()
                .from(USER_ROLE_ASSIGNMENT)
                .where(USER_ROLE_ASSIGNMENT.USER_ID.eq(userId))
                .orderBy(USER_ROLE_ASSIGNMENT.CREATED_AT.desc())
                .fetch(this::mapUserRoleAssignmentRecord);
    }

    public List<UserRoleAssignment> findUserRoleAssignmentsByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(USER_ROLE_ASSIGNMENT)
                .where(USER_ROLE_ASSIGNMENT.WORKSPACE_ID.eq(workspaceId))
                .orderBy(USER_ROLE_ASSIGNMENT.CREATED_AT.desc())
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
        dsl.deleteFrom(USER_ROLE_ASSIGNMENT)
                .where(USER_ROLE_ASSIGNMENT.USER_ID.eq(userId))
                .and(USER_ROLE_ASSIGNMENT.ROLE_ID.in(
                        dsl.select(ROLE.ID)
                                .from(ROLE)
                                .where(ROLE.ROLE_KEY.eq(roleKey))))
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
        dsl.deleteFrom(USER_ROLE_ASSIGNMENT)
                .where(USER_ROLE_ASSIGNMENT.USER_ID.eq(userId))
                .and(USER_ROLE_ASSIGNMENT.WORKSPACE_ID.eq(workspaceId))
                .and(USER_ROLE_ASSIGNMENT.ROLE_ID.in(
                        dsl.select(ROLE.ID)
                                .from(ROLE)
                                .where(ROLE.ROLE_KEY.eq(roleKey))))
                .execute();
    }

    public GroupRoleAssignment saveGroupRoleAssignment(GroupRoleAssignment assignment) {
        dsl.insertInto(GROUP_ROLE_ASSIGNMENT)
                .columns(GROUP_ROLE_ASSIGNMENT.ID, GROUP_ROLE_ASSIGNMENT.WORKSPACE_ID, GROUP_ROLE_ASSIGNMENT.GROUP_ID,
                        GROUP_ROLE_ASSIGNMENT.ROLE_ID, GROUP_ROLE_ASSIGNMENT.ASSIGNED_AT)
                .values(assignment.id(), assignment.workspaceId(), assignment.groupId(),
                        assignment.roleId(),
                        LocalDateTime.ofInstant(assignment.assignedAt(), ZoneOffset.UTC))
                .execute();
        return assignment;
    }

    public List<GroupRoleAssignment> findGroupRoleAssignmentsByGroupId(String groupId) {
        return dsl.select()
                .from(GROUP_ROLE_ASSIGNMENT)
                .where(GROUP_ROLE_ASSIGNMENT.GROUP_ID.eq(groupId))
                .orderBy(GROUP_ROLE_ASSIGNMENT.ASSIGNED_AT.desc())
                .fetch(this::mapGroupRoleAssignmentRecord);
    }

    private Role mapRoleRecord(Record record) {
        return new Role(
                record.get(ROLE.ID, String.class),
                record.get(ROLE.ROLE_KEY, String.class),
                record.get(ROLE.NAME, String.class),
                record.get(ROLE.DESCRIPTION, String.class),
                Role.RoleScope.valueOf(record.get(ROLE.SCOPE, String.class)),
                record.get(ROLE.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }

    private Permission mapPermissionRecord(Record record) {
        return new Permission(
                record.get(PERMISSION.ID, String.class),
                record.get(PERMISSION.PERMISSION_KEY, String.class),
                record.get(PERMISSION.NAME, String.class),
                record.get(PERMISSION.DESCRIPTION, String.class),
                record.get(PERMISSION.RESOURCE_TYPE, String.class),
                record.get(PERMISSION.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }

    private UserRoleAssignment mapUserRoleAssignmentRecord(Record record) {
        return new UserRoleAssignment(
                record.get(USER_ROLE_ASSIGNMENT.ID, String.class),
                record.get(USER_ROLE_ASSIGNMENT.TENANT_ID, String.class),
                record.get(USER_ROLE_ASSIGNMENT.WORKSPACE_ID, String.class),
                record.get(USER_ROLE_ASSIGNMENT.USER_ID, String.class),
                record.get(USER_ROLE_ASSIGNMENT.ROLE_ID, String.class),
                record.get(USER_ROLE_ASSIGNMENT.ASSIGNED_BY, String.class),
                record.get(USER_ROLE_ASSIGNMENT.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }

    private GroupRoleAssignment mapGroupRoleAssignmentRecord(Record record) {
        return new GroupRoleAssignment(
                record.get(GROUP_ROLE_ASSIGNMENT.ID, String.class),
                record.get(GROUP_ROLE_ASSIGNMENT.WORKSPACE_ID, String.class),
                record.get(GROUP_ROLE_ASSIGNMENT.GROUP_ID, String.class),
                record.get(GROUP_ROLE_ASSIGNMENT.ROLE_ID, String.class),
                record.get(GROUP_ROLE_ASSIGNMENT.ASSIGNED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }
}
