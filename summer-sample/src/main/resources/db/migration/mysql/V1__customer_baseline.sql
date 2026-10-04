create table if not exists summer_customer (
    id bigint not null auto_increment,
    username varchar(64) not null,
    email varchar(200) not null,
    display_name varchar(200) not null,
    active boolean not null default true,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint pk_summer_customer primary key (id),
    constraint uk_summer_customer_username unique (username),
    constraint uk_summer_customer_email unique (email)
) engine = InnoDB;
