create table summer_interface_service_catalog (
    service_name varchar(100) character set utf8mb4 collate utf8mb4_bin not null primary key,
    revision bigint not null,
    foreign key (revision) references summer_interface_resource_revision(revision)
);
