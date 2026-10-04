create table if not exists summer_system_parameter_event_receipt (
    event_id varchar(36) not null primary key,
    status varchar(20) not null,
    attempts int not null default 0,
    last_error varchar(500) null,
    updated_at datetime(6) not null,
    constraint ck_summer_system_parameter_event_receipt_status check (status in ('PROCESSED', 'RETRY', 'DEAD_LETTER')),
    constraint ck_summer_system_parameter_event_receipt_attempts check (attempts >= 0)
) engine = InnoDB;
