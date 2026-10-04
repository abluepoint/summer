create table if not exists summer_plugin (
    plugin_id varchar(120) not null primary key,
    version varchar(40) not null,
    artifact_digest varchar(180) not null,
    status varchar(24) not null,
    trusted_flag boolean not null,
    diagnostic varchar(500) not null,
    revision bigint not null,
    registered_by varchar(200) null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint ck_summer_plugin_status check (status in ('REGISTERED', 'ENABLED', 'DISABLED', 'INCOMPATIBLE'))
) engine = InnoDB;
