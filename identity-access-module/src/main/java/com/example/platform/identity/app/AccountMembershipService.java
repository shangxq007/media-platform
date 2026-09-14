package com.example.platform.identity.app;

import com.example.platform.identity.api.account.*;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical user table remains the sole tenant-membership store. */
@Service
public class AccountMembershipService implements AccountIdentityQueries {
    private final JdbcTemplate jdbc;private final UserRepository users;
    private final org.springframework.beans.factory.ObjectProvider<WorkspaceService> workspaces;
    public AccountMembershipService(JdbcTemplate jdbc,UserRepository users,org.springframework.beans.factory.ObjectProvider<WorkspaceService> workspaces) { this.jdbc=jdbc;this.users=users;this.workspaces=workspaces; }

    @Override
    public AccountMembership resolve(String issuer, String subject, String tenant) {
        if (issuer==null || subject==null || tenant==null) throw denied();
        var rows=jdbc.query("""
                select a.id account_id,u.id membership_id,u.tenant_id,u.role
                from account a join "user" u on u.account_id=a.id join tenant t on t.id=u.tenant_id
                where a.issuer=? and a.subject=? and u.tenant_id=?
                """,(r,n)->new AccountMembership(r.getString("account_id"),r.getString("membership_id"),r.getString("tenant_id"),r.getString("role")),issuer,subject,tenant);
        if(rows.size()!=1||!users.isUsableMembership(rows.getFirst().membershipId(),tenant)) throw denied();return rows.getFirst();
    }

    /** Trusted verified-identity onboarding/import command; no public HTTP identity-claim endpoint. */
    @Transactional
    public String provisionVerifiedAccount(CanonicalActor operator, String issuer, String subject) {
        requireProvisioner(operator);requireText(issuer);requireText(subject);
        String id="acct_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("insert into account(id,issuer,subject,status,created_at) values (?,?,?,'ACTIVE',now()) on conflict (issuer,subject) do nothing",id,issuer,subject);
        return jdbc.queryForObject("select id from account where issuer=? and subject=? and status='ACTIVE'",String.class,issuer,subject);
    }

    /** Create-if-absent membership; never updates another tenant or silently changes an existing role. */
    @Transactional
    public AccountMembership createMembership(CanonicalActor operator,String account,String tenant,String username,String email,String role) {
        requireProvisioner(operator);
        var desired=com.example.platform.identity.domain.User.UserRole.valueOf(role);
        if(jdbc.queryForList("select id from account where id=? and status='ACTIVE' for update",String.class,account).size()!=1)throw denied();
        if(jdbc.queryForList("select id from tenant where id=? and status='ACTIVE'",String.class,tenant).size()!=1)throw denied();
        var existing=jdbc.query("select id,role from \"user\" where account_id=? and tenant_id=?",(r,n)->new AccountMembership(account,r.getString("id"),tenant,r.getString("role")),account,tenant);
        if(!existing.isEmpty()) {
            if(existing.size()!=1||!existing.getFirst().role().equals(role))throw new PlatformException(CommonErrorCode.CONFLICT,"Membership already exists with different role");
            return existing.getFirst();
        }
        var user=new com.example.platform.identity.domain.User("usr_"+UUID.randomUUID().toString().replace("-",""),tenant,username,email,desired,
                com.example.platform.identity.domain.User.UserStatus.ACTIVE,java.time.Instant.now());
        users.save(user);return linkMembership(operator,account,tenant,user.id());
    }

    /** Explicitly links an established membership; its ID, roles and audit references are retained. */
    @Transactional
    public AccountMembership linkMembership(CanonicalActor operator, String account, String tenant, String membership) {
        requireProvisioner(operator);
        var accounts=jdbc.queryForList("select id from account where id=? and status='ACTIVE' for update",String.class,account);
        if(accounts.size()!=1) throw denied();
        var rows=jdbc.queryForList("select account_id from \"user\" where id=? and tenant_id=? for update",membership,tenant);
        if(rows.size()!=1) throw denied();
        Object prior=rows.getFirst().get("account_id");
        if(prior!=null && !account.equals(prior))throw new PlatformException(CommonErrorCode.CONFLICT,"Membership already linked");
        var existing=jdbc.queryForList("select id from \"user\" where account_id=? and tenant_id=?",String.class,account,tenant);
        if(!existing.isEmpty()&&!existing.equals(List.of(membership)))throw new PlatformException(CommonErrorCode.CONFLICT,"Account already has a different tenant membership");
        jdbc.update("update \"user\" set account_id=? where id=? and tenant_id=?",account,membership,tenant);
        return jdbc.queryForObject("select id,tenant_id,role from \"user\" where id=?",(r,n)->new AccountMembership(account,r.getString("id"),r.getString("tenant_id"),r.getString("role")),membership);
    }
    @Transactional
    public void setMembershipActive(CanonicalActor operator,String account,String tenant,boolean active) {
        requireProvisioner(operator);
        var ids=jdbc.queryForList("select id from \"user\" where account_id=? and tenant_id=? for update",String.class,account,tenant);
        if(ids.size()!=1)throw denied();
        if(!active)workspaces.getObject().protectOwnershipBeforeDisablingMembership(tenant,ids.getFirst());
        jdbc.update("update \"user\" set status=? where id=? and tenant_id=?",active?"ACTIVE":"INACTIVE",ids.getFirst(),tenant);
    }

    private static void requireProvisioner(CanonicalActor actor) {
        if(actor==null||!actor.isSystem()||!"system:identity-provisioning".equals(actor.actorId()))throw denied();
    }
    private static void requireText(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Verified identity key required");}
    private static PlatformException denied(){return new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Account membership unavailable");}
}
