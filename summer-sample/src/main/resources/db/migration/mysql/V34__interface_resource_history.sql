create table summer_interface_resource_revision (
    revision bigint not null auto_increment,
    fingerprint varchar(64) not null,
    snapshot_json longtext not null,
    resource_count integer not null,
    created_at timestamp(6) not null,
    primary key (revision),
    constraint uk_interface_resource_revision_fingerprint unique (fingerprint)
);
