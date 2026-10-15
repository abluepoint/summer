package com.abluepoint.summer.sample.configuration;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="summer_configuration_override", uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","resource","config_key","scope_id"}))
class ConfigurationOverrideEntity {
    @Id @Column(length=36) String id;
    @Column(name="tenant_id", nullable=false, length=80) String tenantId;
    @Column(nullable=false,length=160) String resource;
    @Column(name="config_key", nullable=false,length=160) String key;
    @Column(name="scope_id", nullable=false,length=64) String scopeId;
    @Column(name="organization_id",length=64) String organizationId;
    @Column(name="created_by",nullable=false,length=128) String createdBy;
    @Column(name="owner_id",nullable=false,length=128) String ownerId;
    @Column(name="value_json",nullable=false,columnDefinition="text") String valueJson;
    @Column(name="public_readable",nullable=false) boolean publicReadable;
    @Column(nullable=false) boolean enabled;
    @Version long version;
    @Column(name="latest_revision",nullable=false) long latestRevision;
    @Column(name="published_revision") Long publishedRevision;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected ConfigurationOverrideEntity() {}
}
