create table if not exists summer_dictionary_type (
    id varchar(36) not null primary key,
    code varchar(80) not null,
    name varchar(200) not null,
    published boolean not null default false,
    public_readable boolean not null default false,
    enabled boolean not null default true,
    constraint uk_summer_dictionary_type_code unique (code)
) engine = InnoDB;

create table if not exists summer_dictionary_item (
    id varchar(36) not null primary key,
    dictionary_code varchar(80) not null,
    item_value varchar(80) not null,
    label varchar(200) not null,
    sort_order int not null,
    enabled boolean not null default true,
    constraint uk_summer_dictionary_item_value unique (dictionary_code, item_value),
    constraint fk_summer_dictionary_item_type foreign key (dictionary_code)
        references summer_dictionary_type (code)
) engine = InnoDB;

create index ix_summer_dictionary_item_order
    on summer_dictionary_item (dictionary_code, enabled, sort_order, item_value);
