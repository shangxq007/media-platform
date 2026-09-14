package com.example.platform.identity.api.account;
/** Account platform administration and tenant membership roles are separate authorities. */
public record AccountMembership(String accountId,String membershipId,String tenantId,String role,boolean platformAdministrator) {
    public AccountMembership(String accountId,String membershipId,String tenantId,String role){this(accountId,membershipId,tenantId,role,false);}
}
