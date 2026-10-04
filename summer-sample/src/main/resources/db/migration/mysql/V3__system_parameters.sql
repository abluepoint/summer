create table if not exists summer_system_parameter (
    id varchar(36) not null primary key,
    tenant_id varchar(80) not null,
    parameter_key varchar(120) not null,
    value_type varchar(20) not null,
    value_json text null,
    required_flag boolean not null default true,
    min_value decimal(38,10) null,
    max_value decimal(38,10) null,
    value_pattern varchar(500) null,
    public_flag boolean not null default false,
    description varchar(500) null,
    status varchar(20) not null default 'ACTIVE',
    version bigint not null default 0,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    constraint uq_summer_system_parameter_key unique (parameter_key),
    constraint ck_summer_system_parameter_type check (value_type in ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN')),
    constraint ck_summer_system_parameter_status check (status in ('ACTIVE', 'INACTIVE'))
) engine = InnoDB;

create index ix_summer_system_parameter_tenant_status
    on summer_system_parameter (tenant_id, status, parameter_key);
