package com.example.platform.identity;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
/** Canonical membership resolvability is required for usable ownership. */
class WorkspaceMembershipResolutionTest extends WorkspaceAuthorityHttpTest {
    @Test void duplicatedMembershipMustNotCountAsAnotherUsableOwner() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"OWNER",creator).statusCode());
        assertEquals(200,call("GET",path(ws),null,member).statusCode(),"healthy second-owner positive control");
        // Baseline addMember inserted duplicate rows; actual schema has no workspace/user uniqueness.
        jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,?,?,'OWNER','ACTIVE',now(),now())","duplicate-"+UUID.randomUUID(),ws,member);
        addOwnerRoleAssignment(ws);
        var before=jdbc.queryForList("select * from workspace_member where workspace_id=? order by id",ws);
        var assignments=jdbc.queryForList("select * from user_role_assignment where workspace_id=? order by id",ws);
        long audits=ownerAudits();
        var secondRead=call("GET",path(ws),null,member);
        assertNotEquals(200,secondRead.statusCode(),"duplicate membership is unusable through canonical actor access");
        var removal=call("DELETE",path(ws)+"/members/"+creator,null,creator);
        var after=jdbc.queryForList("select * from workspace_member where workspace_id=? order by id",ws);
        System.out.println("DUPLICATE_OWNER_PROBE secondRead="+secondRead.statusCode()+" removal="+removal.statusCode()+" remainingActive="+members(ws));
        assertAll(()->assertEquals(409,removal.statusCode(),"do not remove last usable owner"),()->assertEquals(before,after),
                ()->assertEquals(assignments,jdbc.queryForList("select * from user_role_assignment where workspace_id=? order by id",ws)),
                ()->assertEquals(audits,ownerAudits()));
        assertEquals(200,call("GET",path(ws),null,creator).statusCode());
        assertEquals(200,call("GET","/api/workspaces/"+ws+"/entitlements/grants",null,creator).statusCode());
    }

    @Test void anotherStatusOrRoleStillMakesTheCanonicalMembershipAmbiguous() throws Exception {
        for(String status:java.util.List.of("REMOVED","INACTIVE")) {
            String ws=create();assertEquals(200,add(ws,member,"OWNER",creator).statusCode());
            assertEquals(200,call("GET",path(ws),null,member).statusCode());
            jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,?,?,'VIEWER',?,now(),now())","duplicate-"+UUID.randomUUID(),ws,member,status);
            addOwnerRoleAssignment(ws);
            var before=jdbc.queryForList("select * from workspace_member where workspace_id=? order by id",ws);
            var roles=jdbc.queryForList("select * from user_role_assignment where workspace_id=? order by id",ws);long audits=ownerAudits();
            assertNotEquals(200,call("GET",path(ws),null,member).statusCode());
            assertNotEquals(200,call("GET","/api/workspaces/"+ws+"/entitlements/grants",null,member).statusCode());
            var removal=call("DELETE",path(ws)+"/members/"+creator,null,creator);
            assertEquals(409,removal.statusCode(),removal.body());
            assertEquals(before,jdbc.queryForList("select * from workspace_member where workspace_id=? order by id",ws));
            assertEquals(roles,jdbc.queryForList("select * from user_role_assignment where workspace_id=? order by id",ws));assertEquals(audits,ownerAudits());
            assertEquals(200,call("GET",path(ws),null,creator).statusCode());
        }
    }
    @Test void anUnambiguousThirdOwnerAllowsDepartureWithoutRepairingTheMalformedOwner() throws Exception {
        String ws=create();assertEquals(200,add(ws,member,"OWNER",creator).statusCode());assertEquals(200,add(ws,outsider,"OWNER",creator).statusCode());
        jdbc.update("insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values (?,?,?,'OWNER','ACTIVE',now(),now())","duplicate-"+UUID.randomUUID(),ws,member);
        var malformed=jdbc.queryForList("select * from workspace_member where workspace_id=? and user_id=? order by id",ws,member);
        assertNotEquals(200,call("GET",path(ws),null,member).statusCode());
        assertEquals(200,call("GET",path(ws),null,outsider).statusCode());
        assertEquals(204,call("DELETE",path(ws)+"/members/"+creator,null,creator).statusCode());
        assertEquals(403,call("GET",path(ws),null,creator).statusCode());
        assertEquals(200,call("GET",path(ws),null,outsider).statusCode());
        assertEquals(409,call("DELETE",path(ws)+"/members/"+outsider,null,outsider).statusCode());
        assertEquals(malformed,jdbc.queryForList("select * from workspace_member where workspace_id=? and user_id=? order by id",ws,member));
        assertEquals(200,call("GET","/api/workspaces/"+ws+"/entitlements/grants",null,outsider).statusCode());
    }
    @Test void healthyTwoOwnersResolveIndependentlyOfRowsInOtherWorkspaces() throws Exception {
        String ws=create(),other=create();assertEquals(200,add(ws,member,"OWNER",creator).statusCode());
        assertEquals(200,add(other,member,"VIEWER",creator).statusCode());
        assertEquals(200,call("GET",path(ws),null,creator).statusCode());assertEquals(200,call("GET",path(ws),null,member).statusCode());
        assertEquals(204,call("DELETE",path(ws)+"/members/"+creator,null,creator).statusCode());
        assertEquals(200,call("GET",path(ws),null,member).statusCode());
        assertEquals(200,call("GET","/api/workspaces/"+ws+"/entitlements/grants",null,member).statusCode());
        assertEquals(409,call("DELETE",path(ws)+"/members/"+member,null,member).statusCode());
        assertEquals(1,count("select count(*) from workspace_member where workspace_id=? and user_id=? and status='ACTIVE'",other,member));
    }
    private void addOwnerRoleAssignment(String ws) {
        String role="ir2-"+UUID.randomUUID();
        jdbc.update("insert into role(id,role_key,name,scope,created_at) values (?,?,?,'WORKSPACE',now())",role,role,"Owner role regression");
        jdbc.update("insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values (?,?,?,?,?,now())","ir2-"+UUID.randomUUID(),tenant,ws,creator,role);
    }
}
