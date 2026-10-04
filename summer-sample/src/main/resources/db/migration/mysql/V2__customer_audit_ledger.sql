-- Customer operations are append-only audit evidence. The application exposes
-- read-only queries and never accepts update/delete commands for this table.
create table if not exists summer_audit_event (
    event_id varchar(36) not null primary key,
    tenant_id varchar(80) not null,
    organization_id varchar(80) null,
    principal varchar(200) not null,
    resource varchar(240) not null,
    action varchar(80) not null,
    result varchar(40) not null,
    request_id varchar(120) null,
    detail_json text null,
    occurred_at datetime(6) not null,
    constraint ck_summer_audit_result check (result in ('SUCCESS', 'FAILURE'))
) engine = InnoDB;

create index ix_summer_audit_tenant_time
    on summer_audit_event (tenant_id, occurred_at desc);
create index ix_summer_audit_resource_time
    on summer_audit_event (tenant_id, resource, occurred_at desc);
create index ix_summer_audit_request
    on summer_audit_event (tenant_id, request_id);
