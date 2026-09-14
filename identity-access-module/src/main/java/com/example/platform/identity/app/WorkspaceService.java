package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.api.workspace.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.*;
import com.example.platform.shared.audit.AuditPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@org.springframework.transaction.annotation.Transactional
public class WorkspaceService implements com.example.platform.identity.api.workspace.WorkspaceCommands,
        com.example.platform.identity.api.workspace.WorkspaceQueries {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceGroupRepository workspaceGroupRepository;
    private final RoleRepository roleRepository;
    private final AuditPort auditPort;
    private final com.example.platform.identity.api.authorization.CanonicalActorResolver actors;
    private final UserRepository users;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceGroupRepository workspaceGroupRepository,
            RoleRepository roleRepository,
            AuditPort auditPort,
            com.example.platform.identity.api.authorization.CanonicalActorResolver actors, UserRepository users) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceGroupRepository = workspaceGroupRepository;
        this.roleRepository = roleRepository;
        this.auditPort = auditPort;
        this.actors = actors;
        this.users = users;
    }

    public WorkspaceResponse createWorkspace(String tenantId, CreateWorkspaceRequest request) {
        var actor = actor();
        if (!actor.tenantId().equals(tenantId)) throw denied();
        if (request.name() == null || request.name().isBlank()) throw invalid("Workspace name required");
        String id = ("ws_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String planTier = request.planTier() != null ? request.planTier() : "FREE";
        Workspace workspace = new Workspace(id, tenantId, request.name(),
                request.description(), planTier, Workspace.WorkspaceStatus.ACTIVE, now, now);
        workspaceRepository.save(workspace);
        workspaceMemberRepository.save(new WorkspaceMember("wsm_" + java.util.UUID.randomUUID(), id,
                actor.actorId(), "OWNER", WorkspaceMember.MemberStatus.ACTIVE, now, now));
        auditPort.record("USER", "WORKSPACE_CREATE", "CONFIG",
                "WORKSPACE", id, Map.of("tenantId", tenantId, "name", request.name()));
        return WorkspaceResponse.from(workspace);
    }

    public WorkspaceResponse getWorkspace(String workspaceId) {
        Workspace workspace = access(workspaceId, false, false);
        return WorkspaceResponse.from(workspace);
    }

    public WorkspaceMemberResponse addMember(String workspaceId, AddWorkspaceMemberRequest request) {
        Workspace workspace = access(workspaceId, true, true);
        String role = request.role();
        if (!List.of("OWNER", "ADMIN", "EDITOR", "VIEWER").contains(role == null ? "" : role)) throw invalid("Unknown Workspace role");
        if (List.of("OWNER", "ADMIN").contains(role)) requireOwner(workspaceId);
        activeUser(request.userId(), workspace.tenantId());
        var prior = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId());
        if (prior.isPresent() && prior.get().status() == WorkspaceMember.MemberStatus.ACTIVE) {
            if (!prior.get().role().equals(role)) throw conflict("Member already active with another role; remove before changing role");
            return WorkspaceMemberResponse.from(prior.get());
        }
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        WorkspaceMember member;
        if (prior.isPresent()) {
            member = prior.get();
            workspaceMemberRepository.updateRole(member.id(), role, now.atOffset(java.time.ZoneOffset.UTC));
            workspaceMemberRepository.updateStatus(member.id(), WorkspaceMember.MemberStatus.ACTIVE, now.atOffset(java.time.ZoneOffset.UTC));
            member = new WorkspaceMember(member.id(), workspaceId, member.userId(), role, WorkspaceMember.MemberStatus.ACTIVE, member.joinedAt(), now);
        } else {
            member = new WorkspaceMember("wsm_" + java.util.UUID.randomUUID(), workspaceId, request.userId(), role, WorkspaceMember.MemberStatus.ACTIVE, now, now);
            workspaceMemberRepository.save(member);
        }
        auditPort.record("USER", "MEMBER_ADD", "PERMISSION", "WORKSPACE_MEMBER", member.id(),
                Map.of("workspaceId", workspaceId, "userId", request.userId()));
        return WorkspaceMemberResponse.from(member);
    }

    @Override
    public void removeMember(String workspaceId, String userId) {
        Workspace workspace = access(workspaceId, true, true);
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> invalid("Member not found"));
        if (member.status() != WorkspaceMember.MemberStatus.ACTIVE) return;
        if (List.of("OWNER", "ADMIN").contains(member.role())) requireOwner(workspaceId);
        if (member.role().equals("OWNER") && eligibleUser(member.userId(), workspace.tenantId())
                && workspaceMemberRepository.findUnambiguousActiveOwners(workspaceId).stream()
                        .filter(m -> eligibleUser(m.userId(), workspace.tenantId())).count() <= 1)
            throw conflict("Cannot remove the last active Workspace owner");
        roleRepository.deleteMemberAssignments(workspaceId, userId);
        workspaceMemberRepository.updateStatus(member.id(), WorkspaceMember.MemberStatus.REMOVED, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        auditPort.record("USER", "MEMBER_REMOVE", "PERMISSION", "WORKSPACE_MEMBER", member.id(), Map.of("workspaceId", workspaceId, "userId", userId));
    }

    @Override
    public List<WorkspaceResponse> listWorkspacesForUser(String userId) {
        var actor = actor();
        if (!actor.actorId().equals(userId)) throw denied();
        return workspaceRepository.findByTenantId(actor.tenantId()).stream()
                .filter(w -> w.status() == Workspace.WorkspaceStatus.ACTIVE)
                .filter(w -> workspaceMemberRepository.findByWorkspaceIdAndUserId(w.id(), userId)
                        .filter(m -> m.status() == WorkspaceMember.MemberStatus.ACTIVE).isPresent())
                .map(WorkspaceResponse::from).toList();
    }

    public List<WorkspaceMemberResponse> listMembers(String workspaceId) {
        access(workspaceId, false, false);
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    public void assignRoleToMember(String workspaceId, String memberId, AssignRoleRequest request) {
        access(workspaceId, true, true);
        requireOwner(workspaceId);
        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        checkMember(workspaceId, member);
        Role role = roleRepository.findByKey(request.roleKey())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.roleKey()));
        if (roleRepository.findUserRoleAssignmentsByWorkspaceId(workspaceId).stream()
                .anyMatch(a -> a.userId().equals(member.userId()) && a.roleId().equals(role.id()))) return;
        String assignmentId = ("ura_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        UserRoleAssignment assignment = new UserRoleAssignment(
                assignmentId, actor().tenantId(), workspaceId, member.userId(),
                role.id(), actor().actorId(), Instant.now());
        roleRepository.saveUserRoleAssignment(assignment);
        auditPort.record("USER", "ROLE_ASSIGN", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", assignmentId,
                Map.of("workspaceId", workspaceId, "userId", member.userId(), "roleKey", request.roleKey()));
    }

    public void revokeRoleFromMember(String workspaceId, String memberId, String roleKey) {
        access(workspaceId, true, true);
        requireOwner(workspaceId);
        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        checkMember(workspaceId, member);
        // Use workspace-scoped deletion to avoid removing the user's role in OTHER workspaces.
        roleRepository.deleteUserRoleAssignmentByWorkspace(member.userId(), roleKey, workspaceId);
        auditPort.record("USER", "ROLE_REVOKE", "PERMISSION",
                "USER_ROLE_ASSIGNMENT", memberId,
                Map.of("workspaceId", workspaceId, "userId", member.userId(), "roleKey", roleKey));
    }

    public WorkspaceGroupResponse createGroup(String workspaceId, CreateWorkspaceGroupRequest request) {
        access(workspaceId, true, true);
        String id = ("wsg_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        WorkspaceGroup group = new WorkspaceGroup(id, workspaceId, request.name(),
                request.description(), now);
        workspaceGroupRepository.save(group);
        auditPort.record("USER", "GROUP_CREATE", "CONFIG",
                "WORKSPACE_GROUP", id, Map.of("workspaceId", workspaceId, "name", request.name()));
        return WorkspaceGroupResponse.from(group);
    }

    public List<WorkspaceGroupResponse> listGroups(String workspaceId) {
        access(workspaceId, false, false);
        return workspaceGroupRepository.findByWorkspaceId(workspaceId).stream()
                .map(WorkspaceGroupResponse::from)
                .toList();
    }

    /** Hold the owner lock through a surrounding transactional Workspace command adapter. */
    public String requireManagementActor(String workspaceId) {
        access(workspaceId, true, true);
        return actor().actorId();
    }

    /** Entitlement memberId denotes a USER principal, not a membership row ID. */
    public void requireWorkspaceUser(String workspaceId, String userId, boolean active) {
        Workspace workspace = access(workspaceId, false, false);
        // Historical foreign/missing users cannot become entitlement targets.
        var user = users.findById(userId).filter(u -> workspace.tenantId().equals(u.tenantId())).orElseThrow(WorkspaceService::denied);
        if (active) activeUser(user.id(), workspace.tenantId());
        var member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).orElseThrow(WorkspaceService::denied);
        if (active && member.status() != WorkspaceMember.MemberStatus.ACTIVE) throw denied();
    }

    public void requireMemberPreview(String workspaceId, String userId) {
        access(workspaceId, false, !actor().actorId().equals(userId));
        requireWorkspaceUser(workspaceId, userId, true);
    }

    private com.example.platform.shared.authorization.CanonicalActor actor() {
        var actor = actors.resolveCurrentActor().orElseThrow(() -> new com.example.platform.shared.web.PlatformException(
                com.example.platform.shared.web.CommonErrorCode.AUTHENTICATION_REQUIRED, "Authentication required"));
        if (actor.actorType() != com.example.platform.shared.authorization.ActorType.USER || actor.tenantId() == null
                || !actor.tenantId().equals(com.example.platform.shared.web.TenantContext.get())) throw denied();
        activeUser(actor.actorId(), actor.tenantId());
        return actor;
    }

    private void activeUser(String userId, String tenantId) {
        if (!eligibleUser(userId, tenantId)) throw denied();
    }

    /** Same persisted-user eligibility for authenticated actors and usable ownership. */
    private boolean eligibleUser(String userId, String tenantId) {
        return userId != null && users.isUsableMembership(userId, tenantId);
    }

    /** Identity-internal invariant for membership lifecycle commands, under the same owner locks. */
    public void protectOwnershipBeforeDisablingMembership(String tenantId,String userId) {
        for(var workspace:workspaceRepository.findByTenantId(tenantId).stream().sorted(java.util.Comparator.comparing(Workspace::id)).toList()) {
            workspaceRepository.lockById(workspace.id()).orElseThrow();
            var owners=workspaceMemberRepository.findUnambiguousActiveOwners(workspace.id()).stream()
                    .filter(m->eligibleUser(m.userId(),tenantId)).toList();
            if(owners.stream().anyMatch(m->m.userId().equals(userId))&&owners.size()<=1)
                throw conflict("Cannot disable the last usable Workspace owner");
        }
    }

    private Workspace access(String workspaceId, boolean mutate, boolean manage) {
        var actor = actor();
        Workspace workspace = (mutate ? workspaceRepository.lockById(workspaceId) : workspaceRepository.findById(workspaceId))
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        activeUser(actor.actorId(), actor.tenantId());
        if (!actor.tenantId().equals(workspace.tenantId()) || workspace.status() != Workspace.WorkspaceStatus.ACTIVE) throw denied();
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, actor.actorId())
                .filter(m -> m.status() == WorkspaceMember.MemberStatus.ACTIVE).orElseThrow(WorkspaceService::denied);
        if (manage && !List.of("OWNER", "ADMIN").contains(member.role())) throw denied();
        return workspace;
    }

    private void requireOwner(String workspaceId) {
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, actor().actorId())
                .filter(m -> m.status() == WorkspaceMember.MemberStatus.ACTIVE && m.role().equals("OWNER")).isEmpty()) throw denied();
    }

    private void checkMember(String workspaceId, WorkspaceMember member) {
        if (!workspaceId.equals(member.workspaceId()) || member.status() != WorkspaceMember.MemberStatus.ACTIVE) throw denied();
    }
    private static com.example.platform.shared.web.PlatformException denied() {
        return new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.INSUFFICIENT_PERMISSION, "Workspace unavailable");
    }
    private static com.example.platform.shared.web.PlatformException invalid(String message) {
        return new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.INVALID_REQUEST, message);
    }
    private static com.example.platform.shared.web.PlatformException conflict(String message) {
        return new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.CONFLICT, message);
    }
}
