-- Forward correction: V1/V2 checksums remain unchanged. Keep every existing field.
-- No tenant inference, collision precedence, or automatic resolution of quarantined rows.
alter table user_role_assignment add column scope_unresolved boolean not null default false;
update user_role_assignment a set scope_unresolved=true
where not exists(select 1 from "user" u where u.id=a.user_id and u.tenant_id=a.tenant_id)
   or (a.workspace_id is not null and (
       exists(select 1 from project p where p.id=a.workspace_id)
       or not exists(select 1 from workspace w where w.id=a.workspace_id and w.tenant_id=a.tenant_id)))
   or (a.project_id is not null and not exists(
       select 1 from project p where p.id=a.project_id and p.tenant_id=a.tenant_id));
comment on column user_role_assignment.scope_unresolved is
'Historical unproven or colliding scope; excluded from all authorization and ordinary scope removal. Original fields retained. No automatic resolution.';
