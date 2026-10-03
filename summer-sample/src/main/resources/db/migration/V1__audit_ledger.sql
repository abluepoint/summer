-- PostgreSQL production schema for AICT audit evidence (append-only ledger + outbox).
create table if not exists aict_audit_event (
    event_id varchar(160) primary key,
    event_type varchar(80) not null,
    enterprise_id varchar(80) not null,
    request_id varchar(120),
    trace_id varchar(120),
    invocation_id varchar(160) not null,
    attempt_id varchar(160),
    initiator varchar(200),
    actor varchar(200),
    effective_subject varchar(200),
    agent_instance_id varchar(160),
    policy_version integer,
    connection_revision bigint,
    permit_id varchar(160),
    permit_jti varchar(200),
    tool_id varchar(160),
    resource varchar(500),
    action varchar(120),
    decision varchar(40),
    reason_code varchar(120),
    detail_json text,
    occurred_at timestamptz not null,
    previous_digest varchar(128),
    event_digest varchar(128) not null,
    constraint uq_aict_audit_event_event_id unique (event_id)
);

create index if not exists ix_aict_audit_event_enterprise_time
    on aict_audit_event (enterprise_id, occurred_at desc);
create index if not exists ix_aict_audit_event_invocation
    on aict_audit_event (invocation_id, occurred_at asc);

create or replace function aict_reject_audit_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'aict_audit_event is append-only';
end;
$$;
drop trigger if exists trg_aict_audit_event_immutable on aict_audit_event;
create trigger trg_aict_audit_event_immutable
    before update or delete on aict_audit_event
    for each row execute function aict_reject_audit_mutation();

create table if not exists aict_audit_outbox (
    id bigserial primary key,
    event_id varchar(160) not null,
    enterprise_id varchar(80) not null,
    payload_json text not null,
    status varchar(20) not null default 'PENDING',
    attempts integer not null default 0,
    next_attempt_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint uq_aict_audit_outbox_event unique (event_id),
    constraint ck_aict_audit_outbox_attempts check (attempts >= 0),
    constraint ck_aict_audit_outbox_status check (status in ('PENDING', 'PUBLISHED', 'DEAD_LETTER'))
);
create index if not exists ix_aict_audit_outbox_due
    on aict_audit_outbox (status, next_attempt_at, id);
