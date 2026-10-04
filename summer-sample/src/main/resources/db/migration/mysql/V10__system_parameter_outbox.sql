create table if not exists summer_system_parameter_outbox (
    event_id varchar(36) not null primary key,
    tenant_id varchar(80) not null,
    parameter_key varchar(120) not null,
    event_type varchar(80) not null,
    parameter_version bigint not null,
    request_id varchar(120) null,
    payload_json text not null,
    status varchar(20) not null default 'PENDING',
    attempts int not null default 0,
    next_attempt_at datetime(6) not null,
    last_error varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    constraint ck_summer_system_parameter_outbox_status check (status in ('PENDING', 'PUBLISHED', 'DEAD_LETTER')),
    constraint ck_summer_system_parameter_outbox_attempts check (attempts >= 0)
) engine = InnoDB;

create index ix_summer_system_parameter_outbox_due
    on summer_system_parameter_outbox (status, next_attempt_at, created_at);
create index ix_summer_system_parameter_outbox_key
    on summer_system_parameter_outbox (tenant_id, parameter_key, created_at);
