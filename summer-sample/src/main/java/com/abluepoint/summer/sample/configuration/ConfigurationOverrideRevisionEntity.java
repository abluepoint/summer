package com.abluepoint.summer.sample.configuration;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="summer_configuration_override_revision",uniqueConstraints=@UniqueConstraint(columnNames={"override_id","revision"}))
class ConfigurationOverrideRevisionEntity {
    @Id @Column(length=36) String id;
    @Column(name="override_id",nullable=false,length=36) String overrideId;
    @Column(name="tenant_id",nullable=false,length=80) String tenantId;
    @Column(nullable=false) long revision;
    @Column(nullable=false,length=20) String state;
    @Column(name="value_json",nullable=false,columnDefinition="text") String valueJson;
    @Column(name="public_readable",nullable=false) boolean publicReadable;
    @Column(nullable=false) boolean enabled;
    @Column(nullable=false,length=128) String principal;
    @Column(name="request_id",length=128) String requestId;
    @Column(name="created_at",nullable=false) Instant createdAt;
    protected ConfigurationOverrideRevisionEntity() {}
}
