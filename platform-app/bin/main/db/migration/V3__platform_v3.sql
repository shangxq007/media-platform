create table if not exists outbox_events (
  id varchar(64) primary key,
  aggregate_type varchar(100) not null,
  aggregate_id varchar(100) not null,
  event_type varchar(150) not null,
  event_version int not null,
  payload text not null,
  status varchar(50) not null,
  created_at timestamp not null,
  published_at timestamp
);

create table if not exists audit_records (
  id varchar(64) primary key,
  actor_type varchar(50) not null,
  actor_id varchar(100),
  action varchar(120) not null,
  resource_type varchar(120) not null,
  resource_id varchar(120),
  payload text,
  created_at timestamp not null
);

create table if not exists schedules (
  id varchar(64) primary key,
  schedule_code varchar(100) not null,
  handler_code varchar(120) not null,
  enabled boolean not null,
  created_at timestamp not null
);

create table if not exists quota_definitions (
  id varchar(64) primary key,
  quota_code varchar(80) not null,
  unit varchar(50) not null,
  created_at timestamp not null
);
