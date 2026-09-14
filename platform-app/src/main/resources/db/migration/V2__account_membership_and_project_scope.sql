-- Account is a verified global login identity; existing user IDs remain tenant memberships.
-- No email-based linking or inferred historical Project/Workspace allocation.
create table account (
    id varchar(64) primary key,
    issuer varchar(512) not null,
    subject varchar(512) not null,
    platform_admin boolean not null default false,
    status varchar(32) not null,
    created_at timestamp not null,
    unique (issuer, subject)
);
alter table "user" add column account_id varchar(64) references account(id);
create unique index ux_user_account_tenant on "user"(account_id, tenant_id) where account_id is not null;
alter table workspace add constraint ux_workspace_id_tenant unique(id, tenant_id);
alter table project add column workspace_id varchar(64);
alter table project add constraint fk_project_workspace_tenant foreign key(workspace_id, tenant_id) references workspace(id, tenant_id);
comment on column "user".account_id is 'Verified Account link. NULL preserves historical membership awaiting explicit identity mapping; never infer by email.';
comment on column project.workspace_id is 'Canonical resource Workspace within tenant. NULL is an unresolved historical mapping, not a tenant/Project alias.';

-- Once established, resource scope is immutable; unresolved historical mappings may be supplied explicitly.
create function preserve_project_workspace_scope() returns trigger language plpgsql as $$
begin
    if old.workspace_id is not null and (new.workspace_id is distinct from old.workspace_id or new.tenant_id is distinct from old.tenant_id) then
        raise exception 'Established Project scope is immutable';
    end if;
    return new;
end $$;
create trigger project_workspace_scope_immutable before update on project for each row execute function preserve_project_workspace_scope();
alter table project add constraint ux_project_scope unique(id,tenant_id,workspace_id);
create table render_execution_context (
    job_id varchar(64) primary key references render_job(id),
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    project_id varchar(64) not null,
    context_json jsonb not null,
    created_at timestamp not null,
    foreign key(project_id,tenant_id,workspace_id) references project(id,tenant_id,workspace_id)
);
create function preserve_render_execution_context() returns trigger language plpgsql as $$
begin raise exception 'Accepted execution context is immutable'; end $$;
create trigger render_execution_context_immutable before update or delete on render_execution_context for each row execute function preserve_render_execution_context();

-- Preserve the known historical Project-as-Workspace permission encoding without widening it.
alter table user_role_assignment add column project_id varchar(64);
-- Populate missing tenant only from agreeing persisted membership/resource relationships.
update user_role_assignment a set tenant_id=u.tenant_id from "user" u, workspace w
where a.user_id=u.id and a.workspace_id=w.id and u.tenant_id=w.tenant_id and a.tenant_id is null;
update user_role_assignment a set tenant_id=u.tenant_id from "user" u, project p
where a.user_id=u.id and a.workspace_id=p.id and u.tenant_id=p.tenant_id and a.tenant_id is null
  and not exists(select 1 from workspace w where w.id=a.workspace_id);
update user_role_assignment a set project_id=a.workspace_id, workspace_id=null
where exists(select 1 from project p where p.id=a.workspace_id and p.tenant_id=a.tenant_id)
  and not exists(select 1 from workspace w where w.id=a.workspace_id);
-- The documented tenant fallback becomes an explicit tenant-scoped binding only when unambiguous.
update user_role_assignment a set workspace_id=null
where a.workspace_id=a.tenant_id and exists(select 1 from tenant t where t.id=a.tenant_id)
  and not exists(select 1 from workspace w where w.id=a.workspace_id)
  and a.project_id is null;
alter table user_role_assignment add constraint ck_role_assignment_scope check (workspace_id is null or project_id is null);
